package com.bereta.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Développement uniquement : écrit le SMS (code compris) dans les logs au lieu de l'envoyer. */
@Component
@ConditionalOnProperty(prefix = "bereta.sms", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger logger = LoggerFactory.getLogger(LoggingSmsSender.class);

    public LoggingSmsSender() {
        logger.warn("Envoi de SMS simulé (BERETA_SMS_PROVIDER=log) : ne pas utiliser en production.");
    }

    @Override
    public void send(String phoneE164, String message) {
        logger.info("[SMS simulé] à {} : {}", phoneE164, message);
    }
}
