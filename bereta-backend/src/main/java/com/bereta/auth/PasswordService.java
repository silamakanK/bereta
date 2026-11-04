package com.bereta.auth;

import com.bereta.auth.AuthResponses.ForgotPasswordResponse;
import com.bereta.auth.RateLimiterService.Limit;
import com.bereta.auth.otp.OtpChannel;
import com.bereta.auth.otp.OtpCodeEntity;
import com.bereta.auth.otp.OtpService;
import com.bereta.auth.otp.OtpType;
import com.bereta.auth.token.RefreshTokenService;
import com.bereta.notifications.AuthMessages;
import com.bereta.notifications.EmailSender;
import com.bereta.notifications.SmsSender;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Mot de passe oublié (lien par e-mail ou code SMS, 15 min, usage unique, stockés hachés),
 * réinitialisation et changement. Tout changement déconnecte tous les appareils
 * (refresh tokens révoqués + version des access tokens incrémentée) et prévient l'utilisateur.
 */
@Service
public class PasswordService {

    static final Duration LINK_TTL = Duration.ofMinutes(15);
    static final String GENERIC_FORGOT_MESSAGE =
            "Si un compte correspond, vous allez recevoir un code par SMS ou un lien par e-mail.";

    private static final Logger logger = LoggerFactory.getLogger(PasswordService.class);

    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PhoneNumberService phoneNumbers;
    private final OtpService otp;
    private final RefreshTokenService refreshTokens;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final RateLimiterService rateLimiter;
    private final AuthAuditService audit;
    private final Clock clock;
    private final String resetBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                           PasswordEncoder passwordEncoder, PhoneNumberService phoneNumbers, OtpService otp,
                           RefreshTokenService refreshTokens, SmsSender smsSender, EmailSender emailSender,
                           RateLimiterService rateLimiter, AuthAuditService audit, Clock clock,
                           @Value("${bereta.auth.reset.base-url}") String resetBaseUrl) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.phoneNumbers = phoneNumbers;
        this.otp = otp;
        this.refreshTokens = refreshTokens;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
        this.resetBaseUrl = resetBaseUrl.replaceAll("/$", "");
    }

    /** Réponse identique que le compte existe ou non ; un échec d'envoi n'est visible que dans les logs. */
    @Transactional(noRollbackFor = AuthException.class)
    public ForgotPasswordResponse forgot(String identifier) {
        rateLimiter.check(Limit.FORGOT_PER_IP, ClientInfo.current().ipAddress());
        String value = identifier.trim();
        rateLimiter.check(Limit.FORGOT_PER_IDENTIFIER, value.toLowerCase(Locale.ROOT));
        boolean byEmail = value.contains("@");
        UUID challengeId = UUID.randomUUID();

        Optional<UserAccountEntity> account = (byEmail
                ? users.findByEmailIgnoreCase(value)
                : phoneNumbers.tryNormalize(value).flatMap(users::findByPhoneE164))
                .filter(UserAccountEntity::isEnabled);
        account.ifPresent(user -> audit.record(AuthEvent.PASSWORD_RESET_REQUESTED, user.getId(),
                AuthAuditService.hint(value), byEmail ? "Lien par e-mail" : "Code par SMS"));
        if (account.isEmpty()) {
            audit.record(AuthEvent.PASSWORD_RESET_REQUESTED, null, AuthAuditService.hint(value), "Compte inconnu");
        }

        if (byEmail) {
            account.ifPresent(this::sendResetLink);
            return new ForgotPasswordResponse(GENERIC_FORGOT_MESSAGE, OtpChannel.EMAIL, null, LINK_TTL.toSeconds());
        }
        if (account.isPresent()) {
            try {
                challengeId = otp.issue(account.get(), OtpType.PASSWORD_RESET, OtpChannel.SMS,
                        account.get().getPhoneE164()).challengeId();
            } catch (AuthException deliveryFailure) {
                logger.warn("Code de réinitialisation non envoyé : {}", deliveryFailure.getMessage());
            }
        }
        return new ForgotPasswordResponse(GENERIC_FORGOT_MESSAGE, OtpChannel.SMS, challengeId,
                OtpService.TTL.toSeconds());
    }

    @Transactional(noRollbackFor = AuthException.class)
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
    public void resetWithCode(UUID challengeId, String code, String newPassword) {
        rateLimiter.check(Limit.OTP_VERIFY_PER_IP, ClientInfo.current().ipAddress());
        PasswordPolicy.check(newPassword);
        OtpCodeEntity verified = otp.verify(challengeId, OtpType.PASSWORD_RESET, code);
        UserAccountEntity user = users.findByIdForUpdate(verified.getUserAccount().getId())
                .orElseThrow(AuthException::invalidCredentials);
        if (!user.isPhoneVerified() && verified.getDestination().equals(user.getPhoneE164())) {
            user.verifyPhone(verified.getDestination());
        }
        applyNewPassword(user, newPassword, AuthEvent.PASSWORD_RESET_COMPLETED, "Par code SMS");
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

    /** Mot de passe provisoire lisible, remis par le responsable au trésorier. */
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
        notifyPasswordChanged(user);
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

    private void notifyPasswordChanged(UserAccountEntity user) {
        try {
            if (user.getPhoneE164() != null) smsSender.send(user.getPhoneE164(), AuthMessages.PASSWORD_CHANGED_SMS);
            if (user.getEmail() != null && user.isEmailVerified()) {
                emailSender.send(user.getEmail(), AuthMessages.PASSWORD_CHANGED_SUBJECT,
                        AuthMessages.PASSWORD_CHANGED_EMAIL);
            }
        } catch (RuntimeException deliveryFailure) {
            logger.warn("Notification de changement de mot de passe non envoyée : {}", deliveryFailure.getMessage());
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
