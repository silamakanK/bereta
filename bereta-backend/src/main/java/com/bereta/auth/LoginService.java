package com.bereta.auth;

import com.bereta.auth.AuthResponses.LoginStepResponse;
import com.bereta.auth.RateLimiterService.Limit;
import com.bereta.auth.otp.OtpChannel;
import com.bereta.auth.otp.OtpCodeEntity;
import com.bereta.auth.otp.OtpService;
import com.bereta.auth.otp.OtpType;
import com.bereta.auth.token.AccessTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Connexion en deux étapes : identifiant (e-mail ou téléphone) + mot de passe, puis code
 * SMS. Aucun jeton n'est délivré avant la validation du code. Les erreurs restent
 * génériques pour ne jamais révéler si un compte existe.
 */
@Service
public class LoginService {

    static final int MAX_PASSWORD_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumbers;
    private final OtpService otp;
    private final AccessTokenService accessTokens;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;
    private final Clock clock;
    private final String dummyHash;

    public LoginService(UserAccountRepository users, PasswordEncoder passwordEncoder, PhoneNumberService phoneNumbers,
                        OtpService otp, AccessTokenService accessTokens, RateLimiterService rateLimiter,
                        AuthAuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.otp = otp;
        this.accessTokens = accessTokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        // Comparer à un hash factice quand le compte n'existe pas : même temps de réponse.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = AuthException.class)
    public LoginStepResponse start(String identifier, String password) {
        rateLimiter.check(Limit.LOGIN_PER_IP, ClientInfo.current().ipAddress());
        String hint = AuthAuditService.hint(identifier);
        rateLimiter.check(Limit.LOGIN_PER_IDENTIFIER, identifier.trim().toLowerCase(Locale.ROOT));

        Optional<UserAccountEntity> found = findByIdentifier(identifier)
                .flatMap(account -> users.findByIdForUpdate(account.getId()));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(AuthEvent.LOGIN_FAILED, null, hint, "Identifiant inconnu");
            throw AuthException.invalidCredentials();
        }
        UserAccountEntity user = found.get();
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(AuthEvent.LOGIN_FAILED, user.getId(), hint, "Compte verrouillé");
            throw AuthException.invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(MAX_PASSWORD_FAILURES, now.plus(LOCK_DURATION));
            if (user.isLocked(now)) {
                audit.record(AuthEvent.ACCOUNT_LOCKED, user.getId(), hint, "5 mots de passe erronés : 15 minutes");
            } else {
                audit.record(AuthEvent.LOGIN_FAILED, user.getId(), hint, "Mot de passe erroné");
            }
            throw AuthException.invalidCredentials();
        }
        if (!user.isEnabled()) {
            audit.record(AuthEvent.LOGIN_FAILED, user.getId(), hint, "Compte désactivé");
            throw AuthException.invalidCredentials();
        }
        user.resetFailedLogins();
        audit.record(AuthEvent.LOGIN_PASSWORD_ACCEPTED, user.getId(), hint, null);

        if (user.getPhoneE164() == null) {
            return LoginStepResponse.phoneRequired(accessTokens.issuePhoneEnrollmentToken(user));
        }
        rateLimiter.check(Limit.OTP_SEND_PER_IP, ClientInfo.current().ipAddress());
        return LoginStepResponse.otp(otp.issue(user, OtpType.LOGIN, OtpChannel.SMS, user.getPhoneE164()));
    }

    /** Comptes créés avant le téléphone obligatoire : ils l'ajoutent ici, puis le vérifient par SMS. */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginStepResponse enrollPhone(String enrollmentToken, String rawPhone) {
        UUID userId = accessTokens.readPhoneEnrollmentToken(enrollmentToken)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "ENROLLMENT_EXPIRED",
                        "Votre demande a expiré. Reconnectez-vous."));
        UserAccountEntity user = users.findById(userId).filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "ENROLLMENT_EXPIRED",
                        "Votre demande a expiré. Reconnectez-vous."));
        String phone = user.getPhoneE164() != null ? user.getPhoneE164() : phoneNumbers.normalize(rawPhone);
        requirePhoneFree(phone, user.getId());
        rateLimiter.check(Limit.OTP_SEND_PER_IP, ClientInfo.current().ipAddress());
        return LoginStepResponse.otp(otp.issue(user, OtpType.LOGIN, OtpChannel.SMS, phone));
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity complete(UUID challengeId, String code) {
        rateLimiter.check(Limit.OTP_VERIFY_PER_IP, ClientInfo.current().ipAddress());
        OtpCodeEntity verified;
        try {
            verified = otp.verify(challengeId, OtpType.LOGIN, code);
        } catch (AuthException invalidCode) {
            audit.record(AuthEvent.OTP_FAILED, null, null, "Code de connexion refusé");
            throw invalidCode;
        }
        UserAccountEntity user = users.findByIdForUpdate(verified.getUserAccount().getId())
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(AuthException::invalidCredentials);
        String phone = verified.getDestination();
        if (!phone.equals(user.getPhoneE164()) || !user.isPhoneVerified()) {
            requirePhoneFree(phone, user.getId());
            user.verifyPhone(phone);
            audit.record(AuthEvent.PHONE_VERIFIED, user.getId(), AuthAuditService.hint(phone), "À la connexion");
        }
        audit.record(AuthEvent.LOGIN_SUCCEEDED, user.getId(), AuthAuditService.hint(phone), null);
        return user;
    }

    Optional<UserAccountEntity> findByIdentifier(String identifier) {
        String value = identifier.trim();
        if (value.contains("@")) return users.findByEmailIgnoreCase(value);
        return phoneNumbers.tryNormalize(value).flatMap(users::findByPhoneE164);
    }

    private void requirePhoneFree(String phone, UUID ownerId) {
        users.findByPhoneE164(phone).filter(other -> !other.getId().equals(ownerId)).ifPresent(other -> {
            throw new AuthException(HttpStatus.CONFLICT, "PHONE_TAKEN",
                    "Ce numéro est déjà associé à un autre compte.");
        });
    }
}
