package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.ClientInfo;
import com.bereta.auth.UserAccountEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Écriture du journal d'audit, toujours dans la transaction de l'action (MANDATORY) : si l'écriture échoue,
 * l'action est annulée, et une action annulée ne laisse aucune entrée. Les valeurs ne contiennent jamais
 * de mot de passe, de jeton ni de secret (clés filtrées).
 */
@Service
public class AdminAuditService {

    private static final Set<String> SECRET_KEYS = Set.of("password", "motdepasse", "temporarypassword", "token",
            "secret", "hash", "passwordhash", "refreshtoken");

    private final AdminAuditRepository repository;

    public AdminAuditService(AdminAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdminAuditEntity record(UserAccountEntity actor, Entry entry) {
        return repository.save(new AdminAuditEntity(actor, entry.withoutSecrets(), ClientInfo.current().ipAddress()));
    }

    /**
     * Événement de gestion (hors espace administrateur) conservé dans le même historique : création d'association,
     * désignation ou retrait d'un trésorier par un responsable. Texte libre, sans motif ni valeurs.
     */
    @Transactional
    public AdminAuditEntity recordEvent(UserAccountEntity actor, String action, String targetType, UUID targetId,
                                        AssociationEntity association, String details) {
        String safe = details == null ? null
                : details.replaceAll("(?i)(password|token|secret)\\s*[:=]\\s*[^,; ]+", "$1=[masqué]");
        if (safe != null && safe.length() > 1000) safe = safe.substring(0, 1000);
        return repository.save(new AdminAuditEntity(actor, action, targetType, targetId, association, safe,
                ClientInfo.current().ipAddress()));
    }

    /** Contenu d'une entrée : cible, motif (obligatoire côté API) et valeurs avant/après. */
    public record Entry(AdminAction action, String targetType, UUID targetId, AssociationEntity association,
                        String reason, Map<String, Object> before, Map<String, Object> after) {

        Entry withoutSecrets() {
            return new Entry(action, targetType, targetId, association, reason, clean(before), clean(after));
        }

        private static Map<String, Object> clean(Map<String, Object> values) {
            if (values == null) return null;
            Map<String, Object> safe = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (!SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))) safe.put(key, value);
            });
            return safe;
        }
    }
}
