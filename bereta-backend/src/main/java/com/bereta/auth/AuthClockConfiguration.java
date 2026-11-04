package com.bereta.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AuthClockConfiguration {

    /** Horloge injectable : les tests la remplacent pour simuler l'expiration des codes. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
