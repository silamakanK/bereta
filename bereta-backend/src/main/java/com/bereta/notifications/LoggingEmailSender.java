package com.bereta.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Développement uniquement : écrit l'e-mail (lien ou code compris) dans les logs. */
@Component
@ConditionalOnProperty(prefix = "bereta.mail", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger logger = LoggerFactory.getLogger(LoggingEmailSender.class);

    public LoggingEmailSender() {
        logger.warn("Envoi d’e-mails simulé (BERETA_MAIL_PROVIDER=log) : ne pas utiliser en production.");
    }

    @Override
    public void send(String to, String subject, String body) {
        logger.info("[E-mail simulé] à {} — {} :\n{}", to, subject, body);
    }
}
