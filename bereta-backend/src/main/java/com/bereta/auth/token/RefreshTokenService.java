package com.bereta.auth.token;

import com.bereta.auth.AuthAuditService;
import com.bereta.auth.AuthEvent;
import com.bereta.auth.AuthException;
import com.bereta.auth.ClientInfo;
import com.bereta.auth.UserAccountEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
import java.util.UUID;

/**
 * Refresh tokens opaques (32 octets aléatoires), stockés en SHA-256, valables 7 jours,
 * renouvelés à chaque usage (rotation). Présenter un jeton déjà remplacé révoque toute
 * sa famille : un vol de jeton est ainsi détecté et coupé.
 */
@Service
public class RefreshTokenService {

    public static final String COOKIE_NAME = "bereta_refresh";
    public static final String COOKIE_PATH = "/api/auth";

    private final RefreshTokenRepository tokens;
    private final AuthAuditService audit;
    private final Clock clock;
    private final Duration ttl;
    private final boolean secureCookie;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, AuthAuditService audit, Clock clock,
                               @Value("${bereta.auth.refresh.ttl:P7D}") Duration ttl,
                               @Value("${bereta.auth.refresh.cookie-secure:true}") boolean secureCookie) {
        this.tokens = tokens;
        this.audit = audit;
        this.clock = clock;
        this.ttl = ttl;
        this.secureCookie = secureCookie;
    }

    public record Rotation(UserAccountEntity user, String refreshToken) {
    }

    @Transactional
    public String issue(UserAccountEntity user) {
        return issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = AuthException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw sessionExpired();
        RefreshTokenEntity current = tokens.findByHashForUpdate(hash(rawToken)).orElseThrow(this::sessionExpired);
        UserAccountEntity user = current.getUserAccount();
        if (current.getRevokedAt() != null) {
            if ("ROTATED".equals(current.getRevocationReason())) {
                tokens.findAllByFamilyIdAndRevokedAtIsNull(current.getFamilyId())
                        .forEach(token -> token.revoke("REUSE_DETECTED"));
                audit.record(AuthEvent.REFRESH_REUSE_DETECTED, user.getId(), null,
                        "Jeton de rafraîchissement réutilisé : famille révoquée");
            }
            throw sessionExpired();
        }
        if (!current.getExpiresAt().isAfter(clock.instant()) || !user.isEnabled()) {
            current.revoke("EXPIRED");
            throw sessionExpired();
        }
        current.revoke("ROTATED");
        return new Rotation(user, issue(user, current.getFamilyId()));
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        tokens.findByHashForUpdate(hash(rawToken)).ifPresent(token ->
                tokens.findAllByFamilyIdAndRevokedAtIsNull(token.getFamilyId())
                        .forEach(member -> member.revoke("LOGOUT")));
    }

    @Transactional
    public void revokeAll(UUID userId, String reason) {
        tokens.findAllByUserAccountIdAndRevokedAtIsNull(userId).forEach(token -> token.revoke(reason));
    }

    public ResponseCookie cookie(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path(COOKIE_PATH).maxAge(ttl).build();
    }

    public ResponseCookie clearedCookie() {
        return ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path(COOKIE_PATH).maxAge(0).build();
    }

    private String issue(UserAccountEntity user, UUID familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshTokenEntity(user, familyId, hash(raw), ClientInfo.current().userAgent(),
                clock.instant().plus(ttl)));
        return raw;
    }

    private AuthException sessionExpired() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                "Votre session a expiré. Reconnectez-vous.");
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
