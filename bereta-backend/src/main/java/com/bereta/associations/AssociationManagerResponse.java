package com.bereta.associations;

import java.time.Instant;
import java.util.UUID;

public record AssociationManagerResponse(UUID membershipId, UUID userId, String email,
                                         String displayName, boolean active, Instant createdAt) {
    static AssociationManagerResponse from(AssociationMembershipEntity membership) {
        return new AssociationManagerResponse(membership.getId(), membership.getUserAccount().getId(),
                membership.getUserAccount().getEmail(), membership.getUserAccount().getDisplayName(),
                membership.isActive(), membership.getCreatedAt());
    }
}
