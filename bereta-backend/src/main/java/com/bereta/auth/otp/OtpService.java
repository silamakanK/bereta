package com.bereta.auth.otp;

import com.bereta.auth.AuthException;
import com.bereta.auth.PhoneNumberService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.notifications.AuthMessages;
import com.bereta.notifications.EmailSender;
import com.bereta.notifications.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Codes à 6 chiffres : SecureRandom, stockés en HMAC-SHA256 (clé serveur + challenge),
 * valables 5 minutes, usage unique, 5 essais maximum, renvoi après 60 s et au plus
 * 3 renvois par fenêtre de 15 minutes.
 */
@Service
public class OtpService {

    public static final Duration TTL = Duration.ofMinutes(5);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final Duration SEND_WINDOW = Duration.ofMinutes(15);
    /** Envoi initial + 3 renvois. */
    public static final int MAX_SENDS_PER_WINDOW = 4;

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);

    private final OtpCodeRepository codes;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final PhoneNumberService phoneNumbers;
    private final Clock clock;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCodeRepository codes, SmsSender smsSender, EmailSender emailSender,
                      PhoneNumberService phoneNumbers, Clock clock,
                      @Value("${bereta.auth.otp.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("OTP_HASH_SECRET (ou JWT_SECRET) doit contenir au moins 32 octets.");
        }
        this.codes = codes;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
        this.phoneNumbers = phoneNumbers;
        this.clock = clock;
        this.hmacKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Transactional(noRollbackFor = AuthException.class)
    public OtpChallenge issue(UserAccountEntity user, OtpType type, OtpChannel channel, String destination) {
        codes.findAllByUserAccountIdAndTypeAndUsedAtIsNullAndInvalidatedAtIsNull(user.getId(), type)
                .forEach(OtpCodeEntity::invalidate);
        return send(UUID.randomUUID(), user, type, channel, destination);
    }

    /**
     * Renvoie un nouveau code pour le même challenge. Un challenge inconnu reçoit la même
     * réponse qu'un vrai, pour ne pas révéler l'existence d'un compte (mot de passe oublié).
     */
    @Transactional(noRollbackFor = AuthException.class)
    public OtpChallenge resend(UUID challengeId) {
        List<OtpCodeEntity> history = codes.findAllByChallengeForUpdate(challengeId);
        if (history.isEmpty()) {
            return new OtpChallenge(challengeId, null, null, TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
        }
        Instant now = clock.instant();
        OtpCodeEntity latest = history.getFirst();
        Duration sinceLast = Duration.between(latest.getCreatedAt(), now);
        if (sinceLast.compareTo(RESEND_COOLDOWN) < 0) {
            long wait = RESEND_COOLDOWN.minus(sinceLast).toSeconds() + 1;
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_TOO_SOON",
                    "Patientez " + wait + " secondes avant de demander un nouveau code.", wait);
        }
        List<OtpCodeEntity> inWindow = history.stream()
                .filter(code -> code.getCreatedAt().isAfter(now.minus(SEND_WINDOW))).toList();
        if (inWindow.size() >= MAX_SENDS_PER_WINDOW) {
            Instant oldest = inWindow.getLast().getCreatedAt();
            long wait = Math.max(1, Duration.between(now, oldest.plus(SEND_WINDOW)).toSeconds());
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_LIMIT",
                    "Trop de codes envoyés. Réessayez dans " + Math.max(1, wait / 60) + " minute(s).", wait);
        }
        history.forEach(OtpCodeEntity::invalidate);
        return send(challengeId, latest.getUserAccount(), latest.getType(), latest.getChannel(),
                latest.getDestination());
    }

    /** Valide le code et le consomme. Les essais ratés sont conservés (noRollbackFor). */
    @Transactional(noRollbackFor = AuthException.class)
    public OtpCodeEntity verify(UUID challengeId, OtpType expectedType, String submittedCode) {
        Instant now = clock.instant();
        List<OtpCodeEntity> history = challengeId == null ? List.of() : codes.findAllByChallengeForUpdate(challengeId);
        OtpCodeEntity current = history.stream()
                .filter(code -> code.getType() == expectedType && code.isActive(now))
                .findFirst()
                .orElseThrow(() -> AuthException.badRequest("CODE_INVALID",
                        "Code incorrect ou expiré. Demandez un nouveau code."));

        String code = submittedCode == null ? "" : submittedCode.trim();
        boolean matches = code.matches("\\d{6}") && MessageDigest.isEqual(
                hash(challengeId, code).getBytes(StandardCharsets.US_ASCII),
                current.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            current.recordFailedAttempt(MAX_ATTEMPTS);
            int remaining = MAX_ATTEMPTS - current.getAttempts();
            String message = remaining <= 0
                    ? "Trop d’essais : ce code est bloqué. Demandez un nouveau code."
                    : expectedType == OtpType.PASSWORD_RESET
                    ? "Code incorrect ou expiré. Demandez un nouveau code."
                    : "Code incorrect. Il vous reste " + remaining + (remaining > 1 ? " essais." : " essai.");
            throw AuthException.badRequest("CODE_INVALID", message);
        }
        current.markUsed();
        history.forEach(OtpCodeEntity::invalidate);
        return current;
    }

    private OtpChallenge send(UUID challengeId, UserAccountEntity user, OtpType type, OtpChannel channel,
                              String destination) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        codes.save(new OtpCodeEntity(challengeId, user, type, channel, destination, hash(challengeId, code),
                clock.instant().plus(TTL)));
        try {
            if (channel == OtpChannel.SMS) {
                smsSender.send(destination, switch (type) {
                    case LOGIN -> AuthMessages.loginCode(code);
                    case PASSWORD_RESET -> AuthMessages.passwordResetCode(code);
                    case PHONE_VERIFY, EMAIL_VERIFY -> AuthMessages.phoneVerificationCode(code);
                });
            } else {
                emailSender.send(destination, AuthMessages.EMAIL_VERIFICATION_SUBJECT,
                        AuthMessages.emailVerificationBody(code));
            }
        } catch (RuntimeException deliveryFailure) {
            logger.error("Échec d’envoi d’un code {} par {} : {}", type, channel, deliveryFailure.getMessage());
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "DELIVERY_FAILED",
                    channel == OtpChannel.SMS
                            ? "Le SMS n’a pas pu être envoyé. Réessayez dans un instant."
                            : "L’e-mail n’a pas pu être envoyé. Réessayez dans un instant.");
        }
        String masked = channel == OtpChannel.SMS ? phoneNumbers.mask(destination) : maskEmail(destination);
        return new OtpChallenge(challengeId, channel, masked, TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    String hash(UUID challengeId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((challengeId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", impossible);
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
