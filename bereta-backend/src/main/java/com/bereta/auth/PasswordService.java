package com.bereta.auth;

import com.bereta.auth.RateLimiterService.Limit;
import com.bereta.auth.token.RefreshTokenService;
import com.bereta.notifications.AuthMessages;
import com.bereta.notifications.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Changement de mot de passe, mot de passe provisoire (responsable ou administrateur) et
 * mot de passe oublié par lien e-mail. Ce dernier est désactivé tant que
 * BERETA_FORGOT_PASSWORD_ENABLED=false (à réactiver avec l'envoi d'e-mails Brevo).
 * Tout changement déconnecte tous les appareils (refresh tokens révoqués + version des
 * access tokens incrémentée).
 */
@Service
public class PasswordService {

    static final Duration LINK_TTL = Duration.ofMinutes(15);
    static final String GENERIC_FORGOT_MESSAGE =
            "Si un compte correspond à cette adresse, vous allez recevoir un lien par e-mail.";

    private static final Logger logger = LoggerFactory.getLogger(PasswordService.class);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final EmailSender emailSender;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;
    private final Clock clock;
    private final String resetBaseUrl;
    private final boolean forgotEnabled;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                           PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens,
                           EmailSender emailSender, RateLimiterService rateLimiter, AuthAuditService audit, Clock clock,
                           @Value("${bereta.auth.reset.base-url}") String resetBaseUrl,
                           @Value("${bereta.auth.forgot-password.enabled:false}") boolean forgotEnabled) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        this.resetBaseUrl = resetBaseUrl.replaceAll("/$", "");
        this.forgotEnabled = forgotEnabled;
    }

    /** Réponse identique que le compte existe ou non. Désactivé par défaut. */
    @Transactional
    public String forgot(String identifier) {
        if (!forgotEnabled) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "FORGOT_PASSWORD_DISABLED",
                    "Contactez votre responsable ou l’administrateur pour réinitialiser votre mot de passe.");
        }
        rateLimiter.check(Limit.FORGOT_PER_IP, ClientInfo.current().ipAddress());
        String value = identifier.trim();
        rateLimiter.check(Limit.FORGOT_PER_IDENTIFIER, value.toLowerCase(Locale.ROOT));
        users.findByEmailIgnoreCase(value).filter(UserAccountEntity::isEnabled).ifPresentOrElse(user -> {
            audit.record(AuthEvent.PASSWORD_RESET_REQUESTED, user.getId(), AuthAuditService.hint(value), "Lien par e-mail");
            sendResetLink(user);
        }, () -> audit.record(AuthEvent.PASSWORD_RESET_REQUESTED, null, AuthAuditService.hint(value), "Compte inconnu"));
        return GENERIC_FORGOT_MESSAGE;
    }

    @Transactional
    public void resetWithLink(String token, String newPassword) {
        PasswordPolicy.check(newPassword);
        PasswordResetTokenEntity resetToken = resetTokens.findUnusedForUpdate(sha256(token))
                .filter(candidate -> candidate.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new AuthException(HttpStatus.GONE, "LINK_INVALID",
                        "Ce lien est invalide, expiré ou déjà utilisé. Demandez-en un nouveau."));
        UserAccountEntity user = users.findByIdForUpdate(resetToken.getUserAccount().getId())
                .orElseThrow(() -> new AuthException(HttpStatus.GONE, "LINK_INVALID", "Ce lien est invalide."));
        applyNewPassword(user, newPassword, AuthEvent.PASSWORD_RESET_COMPLETED, "Par lien e-mail");
    }

    @Transactional(noRollbackFor = AuthException.class)
    public UserAccountEntity change(UUID userId, String currentPassword, String newPassword) {
        UserAccountEntity user = users.findByIdForUpdate(userId).orElseThrow(AuthException::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw AuthException.badRequest("CURRENT_PASSWORD_INVALID", "Le mot de passe actuel est incorrect.");
        }
        PasswordPolicy.check(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw AuthException.badRequest("PASSWORD_UNCHANGED", "Choisissez un mot de passe différent de l’actuel.");
        }
        applyNewPassword(user, newPassword, AuthEvent.PASSWORD_CHANGED,
                user.isMustChangePassword() ? "Remplacement du mot de passe provisoire" : "Depuis le profil");
        return user;
    }

    /**
     * Réinitialisation par un administrateur : mot de passe provisoire (à changer à la prochaine
     * connexion), sessions révoquées. Le mot de passe n'est renvoyé qu'une fois, à l'administrateur.
     */
    @Transactional
    public String resetByAdministrator(UUID adminId, UUID userId) {
        UserAccountEntity user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Utilisateur introuvable."));
        String temporaryPassword = generateTemporaryPassword();
        user.setTemporaryPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.resetFailedLogins();
        user.bumpTokenVersion();
        refreshTokens.revokeAll(user.getId(), "ADMIN_RESET");
        resetTokens.findAllByUserAccountIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetTokenEntity::markUsed);
        audit.record(AuthEvent.TEMPORARY_PASSWORD_ISSUED, user.getId(), null, "Par l’administrateur " + adminId);
        return temporaryPassword;
    }

    /** Mot de passe provisoire lisible (10 caractères dont au moins 1 chiffre). */
    public String generateTemporaryPassword() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) password.append(alphabet.charAt(random.nextInt(alphabet.length())));
        password.setCharAt(random.nextInt(10), (char) ('2' + random.nextInt(8)));
        return password.toString();
    }

    private void applyNewPassword(UserAccountEntity user, String newPassword, AuthEvent event, String details) {
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.resetFailedLogins();
        user.bumpTokenVersion();
        refreshTokens.revokeAll(user.getId(), "PASSWORD_CHANGED");
        resetTokens.findAllByUserAccountIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetTokenEntity::markUsed);
        audit.record(event, user.getId(), null, details);
    }

    private void sendResetLink(UserAccountEntity user) {
        resetTokens.findAllByUserAccountIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetTokenEntity::markUsed);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        resetTokens.save(new PasswordResetTokenEntity(user, sha256(token), clock.instant().plus(LINK_TTL)));
        try {
            emailSender.send(user.getEmail(), AuthMessages.PASSWORD_RESET_SUBJECT,
                    AuthMessages.passwordResetLinkBody(resetBaseUrl + "/reinitialiser-mot-de-passe?token=" + token));
        } catch (RuntimeException deliveryFailure) {
            logger.warn("Lien de réinitialisation non envoyé : {}", deliveryFailure.getMessage());
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
