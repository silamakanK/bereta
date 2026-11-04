package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminAuditService {
    private final AdminAuditRepository repository;
    public AdminAuditService(AdminAuditRepository repository) { this.repository = repository; }

    @Transactional
    public void record(UserAccountEntity actor, String action, String targetType, UUID targetId,
                       AssociationEntity association, String details) {
        repository.save(new AdminAuditEntity(actor, action, targetType, targetId, association, safe(details)));
    }

    @Transactional
    public void recordCommand(String action, String targetType, UUID targetId, String details) {
        repository.save(new AdminAuditEntity(null, action, targetType, targetId, null, safe(details)));
    }

    private String safe(String details) {
        if (details == null) return null;
        String safe = details.replaceAll("(?i)(password|token|secret)\\s*[:=]\\s*[^,; ]+", "$1=[masqué]");
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }
}
