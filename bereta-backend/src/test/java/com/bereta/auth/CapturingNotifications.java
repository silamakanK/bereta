package com.bereta.auth;

import com.bereta.notifications.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Capture les e-mails envoyés et fournit une horloge réglable (verrouillage, expiration). */
@TestConfiguration
public class CapturingNotifications {

    public record Message(String to, String text) {
    }

    public static final List<Message> EMAILS = new CopyOnWriteArrayList<>();
    public static final MutableClock CLOCK = new MutableClock();

    @Bean
    @Primary
    EmailSender capturingEmailSender() {
        return (to, subject, body) -> EMAILS.add(new Message(to, body));
    }

    @Bean
    @Primary
    Clock mutableClock() {
        return CLOCK;
    }

    public static void reset() {
        EMAILS.clear();
        CLOCK.reset();
    }

    public static final class MutableClock extends Clock {
        private volatile Duration offset = Duration.ZERO;

        public void advance(Duration duration) { offset = offset.plus(duration); }
        void reset() { offset = Duration.ZERO; }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.now().plus(offset); }
    }
}
