package com.bereta.auth.token;

import com.bereta.auth.UserAccountEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Access tokens (15 min, claim typ=access) et jetons d'enrôlement du téléphone
 * (10 min, typ=phone_enrollment). Le convertisseur n'accepte que typ=access sur l'API.
 */
@Service
public class AccessTokenService {

    public static final String TYPE_CLAIM = "typ";
    public static final String ACCESS = "access";
    public static final String PHONE_ENROLLMENT = "phone_enrollment";
    public static final String VERSION_CLAIM = "ver";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;
    private final Duration accessTtl;

    public AccessTokenService(JwtEncoder encoder, JwtDecoder decoder, Clock clock,
                              @Value("${bereta.auth.jwt.access-ttl:PT15M}") Duration accessTtl) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
    }

    public Duration accessTtl() { return accessTtl; }

    public String issueAccessToken(UserAccountEntity user) {
        return encode(user.getId(), ACCESS, accessTtl, user.getTokenVersion(), user.getRole());
    }

    public String issuePhoneEnrollmentToken(UserAccountEntity user) {
        return encode(user.getId(), PHONE_ENROLLMENT, Duration.ofMinutes(10), user.getTokenVersion(), null);
    }

    public Optional<UUID> readPhoneEnrollmentToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!PHONE_ENROLLMENT.equals(jwt.getClaimAsString(TYPE_CLAIM))) return Optional.empty();
            return Optional.of(UUID.fromString(jwt.getSubject()));
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private String encode(UUID subject, String type, Duration ttl, int version, String role) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfiguration.ISSUER)
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(TYPE_CLAIM, type)
                .claim(VERSION_CLAIM, version);
        if (role != null) claims.claim("role", role);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
