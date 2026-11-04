package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminAssociationResponse(UUID id, String name, String description, String contactEmail,
        String contactPhone, String address, AssociationStatus status, Instant createdAt,
        long primaryFundCount, long localFundCount, long memberCount, List<String> responsibleEmails) {
    static AdminAssociationResponse from(AssociationEntity association, long primaryFunds,
                                          long localFunds, long members, List<String> responsibleEmails) {
        return new AdminAssociationResponse(association.getId(), association.getName(), association.getDescription(),
                association.getContactEmail(), association.getContactPhone(), association.getAddress(),
                association.getStatus(), association.getCreatedAt(), primaryFunds, localFunds, members, responsibleEmails);
    }
}
