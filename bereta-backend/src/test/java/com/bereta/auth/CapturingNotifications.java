package com.bereta.auth;

import com.bereta.notifications.EmailSender;
import com.bereta.notifications.SmsSender;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Capture les SMS et e-mails envoyés et fournit une horloge réglable. */
@TestConfiguration
public class CapturingNotifications {

    public record Message(String to, String text) {
    }

    public static final List<Message> SMS = new CopyOnWriteArrayList<>();
    public static final List<Message> EMAILS = new CopyOnWriteArrayList<>();
    public static final MutableClock CLOCK = new MutableClock();

    @Bean
    @Primary
    SmsSender capturingSmsSender() {
        return (phone, message) -> SMS.add(new Message(phone, message));
    }

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
        SMS.clear();
        EMAILS.clear();
        CLOCK.reset();
    }

    public static String lastCodeSentTo(String destination) {
        List<Message> all = new java.util.ArrayList<>(SMS);
        all.addAll(EMAILS);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).to().equals(destination)) {
                Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(all.get(i).text());
                if (matcher.find()) return matcher.group(1);
            }
        }
        throw new AssertionError("Aucun code envoyé à " + destination);
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
