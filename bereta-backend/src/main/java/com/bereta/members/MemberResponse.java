package com.bereta.members;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code localFundId} et {@code fundName} : caisse de rattachement du membre. Sans caisse locale,
 * le membre est suivi directement par la caisse principale ({@code inPrimaryFund}).
 */
public record MemberResponse(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        UUID localFundId,
        String fundName,
        boolean inPrimaryFund,
        boolean active,
        Instant createdAt
) {
    static MemberResponse from(MemberEntity member) {
        var local = member.getCreatedInLocalFund();
        var primary = member.getCreatedInPrimaryFund();
        return new MemberResponse(
                member.getId(),
                member.getFirstName(),
                member.getLastName(),
                member.getPhone(),
                local == null ? null : local.getId(),
                local != null ? local.getName() : primary != null ? "Caisse principale" : null,
                local == null && primary != null,
                member.isActive(),
                member.getCreatedAt()
        );
    }
}
