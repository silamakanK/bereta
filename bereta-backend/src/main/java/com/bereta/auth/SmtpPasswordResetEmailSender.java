package com.bereta.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "bereta.auth.mail", name = "enabled", havingValue = "true")
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetEmailSender(JavaMailSender mailSender,
                                        @Value("${bereta.auth.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String email, String resetUrl) {
        if (from.isBlank()) throw new IllegalStateException("MAIL_FROM must be configured when mail is enabled.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Réinitialiser votre mot de passe BERETA");
        message.setText("Une demande de réinitialisation du mot de passe de votre compte BERETA a été faite.\n\n"
                + "Pour choisir un nouveau mot de passe, ouvrez ce lien (valable 30 minutes) :\n"
                + resetUrl + "\n\nSi vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail.");
        mailSender.send(message);
    }
}
