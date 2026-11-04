package com.bereta.auth;

import com.bereta.auth.AuthRequests.Register;
import com.bereta.auth.AuthResponses.CodeSentResponse;
import com.bereta.auth.AuthResponses.RegistrationResponse;
import com.bereta.auth.RateLimiterService.Limit;
import com.bereta.auth.otp.OtpChannel;
import com.bereta.auth.otp.OtpCodeEntity;
import com.bereta.auth.otp.OtpService;
import com.bereta.auth.otp.OtpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Inscription d'un responsable : téléphone obligatoire et vérifié par SMS (le compte reste
 * en attente jusque-là), e-mail facultatif et vérifié s'il est saisi.
 */
@Service
public class RegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumbers;
    private final OtpService otp;
    private final EmailVerificationService emailVerification;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;

    public RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                               PhoneNumberService phoneNumbers, OtpService otp,
                               EmailVerificationService emailVerification, RateLimiterService rateLimiter,
                               AuthAuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.otp = otp;
        this.emailVerification = emailVerification;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public RegistrationResponse register(Register request) {
        rateLimiter.check(Limit.REGISTER_PER_IP, ClientInfo.current().ipAddress());
        if (!request.acceptTerms()) {
            throw AuthException.badRequest("TERMS_REQUIRED", "Acceptez les conditions d’utilisation pour continuer.");
        }
        PasswordPolicy.check(request.password());
        String phone = phoneNumbers.normalize(request.phone());
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = request.firstName().trim() + " " + request.lastName().trim();
        if (displayName.length() > 120) {
            throw AuthException.badRequest("NAME_TOO_LONG",
                    "Le prénom et le nom ne peuvent pas dépasser 120 caractères au total.");
        }
        if (users.existsByPhoneE164(phone)) throw phoneTaken();
        if (email != null && users.existsByEmailIgnoreCase(email)) throw emailTaken();

        UserAccountEntity user = new UserAccountEntity(email, phone, displayName,
                passwordEncoder.encode(request.password()), "RESPONSABLE", AccountStatus.PENDING_VERIFICATION);
        user.acceptTerms();
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            // Inscription simultanée avec le même numéro ou e-mail (session Hibernate inutilisable ici).
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                    "Ce numéro ou cette adresse e-mail est déjà utilisé. Connectez-vous.");
        }
        audit.record(AuthEvent.REGISTERED, user.getId(), AuthAuditService.hint(phone), null);

        CodeSentResponse phoneChallenge = CodeSentResponse.from(
                otp.issue(user, OtpType.PHONE_VERIFY, OtpChannel.SMS, phone));
        boolean emailSent = false;
        if (email != null) {
            try {
                emailVerification.send(user);
                emailSent = true;
            } catch (AuthException deliveryFailure) {
                logger.warn("E-mail de vérification non envoyé à l’inscription : {}", deliveryFailure.getMessage());
            }
        }
        return new RegistrationResponse(phoneChallenge, emailSent);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity verifyPhone(UUID challengeId, String code) {
        rateLimiter.check(Limit.OTP_VERIFY_PER_IP, ClientInfo.current().ipAddress());
        OtpCodeEntity verified = otp.verify(challengeId, OtpType.PHONE_VERIFY, code);
        UserAccountEntity user = users.findByIdForUpdate(verified.getUserAccount().getId())
                .orElseThrow(AuthException::invalidCredentials);
        user.verifyPhone(verified.getDestination());
        audit.record(AuthEvent.PHONE_VERIFIED, user.getId(), AuthAuditService.hint(verified.getDestination()),
                "À l’inscription");
        return user;
    }

    private static AuthException phoneTaken() {
        return new AuthException(HttpStatus.CONFLICT, "PHONE_TAKEN",
                "Ce numéro de téléphone est déjà utilisé. Connectez-vous ou utilisez « Mot de passe oublié ».");
    }

    private static AuthException emailTaken() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                "Cette adresse e-mail est déjà utilisée. Connectez-vous ou laissez le champ vide.");
    }
}
