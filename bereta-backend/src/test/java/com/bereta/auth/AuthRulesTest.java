package com.bereta.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRulesTest {

    private final PhoneNumberService phones = new PhoneNumberService("FR");

    @Test
    void normalizesUsualFrenchAndInternationalFormatsToE164() {
        assertThat(phones.normalize("06 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(phones.normalize("06.12.34.56.78")).isEqualTo("+33612345678");
        assertThat(phones.normalize("+33 6 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(phones.normalize("0033612345678")).isEqualTo("+33612345678");
        assertThat(phones.normalize("+223 76 12 34 56")).isEqualTo("+22376123456");
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThat(phones.tryNormalize("12")).isEmpty();
        assertThat(phones.tryNormalize("bonjour")).isEmpty();
        assertThat(phones.tryNormalize("")).isEmpty();
        assertThatThrownBy(() -> phones.normalize("0612"))
                .isInstanceOfSatisfying(AuthException.class, e -> assertThat(e.getCode()).isEqualTo("PHONE_INVALID"));
    }

    @Test
    void masksAllButFirstAndLastDigits() {
        assertThat(phones.mask("+33612345678")).isEqualTo("+33 6 ** ** ** 78");
        assertThat(phones.mask("+22376123456")).isEqualTo("+223 7 ** ** ** 56");
    }

    @Test
    void passwordPolicyFollowsTheMockup() {
        PasswordPolicy.check("motdepasse1");
        assertThatThrownBy(() -> PasswordPolicy.check("court1")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("sanschiffre")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> PasswordPolicy.check("é".repeat(37) + "1")).isInstanceOf(AuthException.class);
    }

    @Test
    void rateLimiterRejectsBeyondCapacityPerKeyAndGivesRetryDelay() {
        RateLimiterService limiter = new RateLimiterService(true);
        for (int i = 0; i < 5; i++) limiter.check(RateLimiterService.Limit.REGISTER_PER_IP, "10.0.0.1");
        assertThatThrownBy(() -> limiter.check(RateLimiterService.Limit.REGISTER_PER_IP, "10.0.0.1"))
                .isInstanceOfSatisfying(AuthException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isPositive();
                });
        limiter.check(RateLimiterService.Limit.REGISTER_PER_IP, "10.0.0.2");
        new RateLimiterService(false).check(RateLimiterService.Limit.REGISTER_PER_IP, "10.0.0.1");
    }

    @Test
    void auditHintNeverContainsTheFullIdentifier() {
        assertThat(AuthAuditService.hint("fatou@exemple.org")).isEqualTo("fa***@exemple.org");
        assertThat(AuthAuditService.hint("+33612345678")).isEqualTo("+336******78");
    }
}
