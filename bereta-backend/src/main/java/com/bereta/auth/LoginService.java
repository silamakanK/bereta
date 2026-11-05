package com.bereta.auth;

import com.bereta.auth.RateLimiterService.Limit;
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
 * Connexion : identifiant (e-mail ou téléphone) + mot de passe, la session est délivrée
 * directement. Erreur toujours générique (« Identifiants incorrects »), verrouillage
 * 15 minutes après 5 mots de passe erronés, limitation de débit par IP et par identifiant.
 */
@Service
public class LoginService {

    static final int MAX_PASSWORD_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumbers;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;
    private final Clock clock;
    private final String dummyHash;

    public LoginService(UserAccountRepository users, PasswordEncoder passwordEncoder, PhoneNumberService phoneNumbers,
                        RateLimiterService rateLimiter, AuthAuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        // Comparer à un hash factice quand le compte n'existe pas : même temps de réponse.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity login(String identifier, String password) {
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
        audit.record(AuthEvent.LOGIN_SUCCEEDED, user.getId(), hint, null);
        return user;
    }

    Optional<UserAccountEntity> findByIdentifier(String identifier) {
        String value = identifier.trim();
        if (value.contains("@")) return users.findByEmailIgnoreCase(value);
        return phoneNumbers.tryNormalize(value).flatMap(users::findByPhoneE164);
    }
}
