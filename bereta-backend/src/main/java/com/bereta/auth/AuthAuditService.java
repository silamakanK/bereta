package com.bereta.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Trace les événements d'authentification dans sa propre transaction, pour qu'un
 * échec (connexion refusée, code faux) reste enregistré. Ne jamais y passer de
 * mot de passe, code ou jeton : seulement un identifiant masqué.
 */
@Service
public class AuthAuditService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthAuditRepository repository;

    public AuthAuditService(AuthAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthEvent event, UUID userId, String identifierHint, String details) {
        try {
            repository.saveAndFlush(new AuthAuditEntity(userId, event, truncate(identifierHint, 64),
                    ClientInfo.current(), truncate(details, 300)));
        } catch (RuntimeException failure) {
            // L'audit ne doit jamais bloquer une connexion ; l'échec est visible dans les logs.
            logger.error("Événement d’authentification {} non journalisé : {}", event, failure.getMessage());
        }
    }

    /** « fatou@exemple.org » → « fa***@exemple.org », « +33612345678 » → « +336*****78 ». */
    public static String hint(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String value = identifier.trim();
        int at = value.indexOf('@');
        if (at > 0) return value.substring(0, Math.min(2, at)) + "***" + value.substring(at);
        if (value.length() <= 4) return "****";
        return value.substring(0, 4) + "*".repeat(Math.max(0, value.length() - 6))
                + value.substring(value.length() - 2);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
