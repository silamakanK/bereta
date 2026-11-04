package com.bereta.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(UUID id, String actorEmail, String action, String targetType,
                                 UUID targetId, UUID associationId, String details, Instant createdAt) {
    static AdminAuditResponse from(AdminAuditEntity entity) {
        return new AdminAuditResponse(entity.getId(), entity.getActorEmail(), entity.getAction(),
                entity.getTargetType(), entity.getTargetId(), entity.getAssociationId(),
                entity.getDetails(), entity.getCreatedAt());
    }
}
