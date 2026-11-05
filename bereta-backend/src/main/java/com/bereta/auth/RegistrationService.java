package com.bereta.auth;

import com.bereta.auth.AuthRequests.Register;
import com.bereta.auth.RateLimiterService.Limit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Inscription d'un responsable : téléphone (identifiant, E.164, unique) + mot de passe + CGU ;
 * e-mail, prénom et nom facultatifs (ou ensuite via « Complète ton profil »). Le compte est
 * actif immédiatement et la session est ouverte dans la foulée.
 */
@Service
public class RegistrationService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumbers;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;

    public RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                               PhoneNumberService phoneNumbers, RateLimiterService rateLimiter,
                               AuthAuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional
    public UserAccountEntity register(Register request) {
        rateLimiter.check(Limit.REGISTER_PER_IP, ClientInfo.current().ipAddress());
        if (!request.acceptTerms()) {
            throw AuthException.badRequest("TERMS_REQUIRED", "Acceptez les conditions d’utilisation pour continuer.");
        }
        PasswordPolicy.check(request.password());
        String phone = phoneNumbers.normalize(request.phone());
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String displayName = ProfileService.displayName(request.firstName(), request.lastName());
        if (users.existsByPhoneE164(phone) || (email != null && users.existsByEmailIgnoreCase(email))) {
            throw alreadyUsed();
        }
        UserAccountEntity user = new UserAccountEntity(email, phone, displayName,
                passwordEncoder.encode(request.password()), "RESPONSABLE");
        user.acceptTerms();
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw alreadyUsed(); // inscription simultanée avec le même numéro ou e-mail
        }
        audit.record(AuthEvent.REGISTERED, user.getId(), AuthAuditService.hint(phone), null);
        return user;
    }

    private static AuthException alreadyUsed() {
        return new AuthException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS",
                "Ce numéro ou cet e-mail a déjà un compte. Connectez-vous.");
    }
}
