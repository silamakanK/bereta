package com.bereta.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP générique (fonctionne aussi avec le relais SMTP de Brevo). */
@Component
@ConditionalOnProperty(prefix = "bereta.mail", name = "provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${bereta.mail.from:}") String from) {
        if (from.isBlank()) throw new IllegalStateException("MAIL_FROM doit être défini quand BERETA_MAIL_PROVIDER=smtp.");
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
