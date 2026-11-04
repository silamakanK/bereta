package com.bereta.associations;

import java.time.Instant;
import java.util.UUID;

public record AssociationResponse(UUID id, String name, String description, String contactEmail,
                                  String contactPhone, String address, AssociationStatus status,
                                  boolean currentUserResponsible, Instant createdAt) {
    static AssociationResponse from(AssociationEntity association, boolean responsible) {
        return new AssociationResponse(association.getId(), association.getName(), association.getDescription(),
                association.getContactEmail(), association.getContactPhone(), association.getAddress(),
                association.getStatus(), responsible, association.getCreatedAt());
    }
}
