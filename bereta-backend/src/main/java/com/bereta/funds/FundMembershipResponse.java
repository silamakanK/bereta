package com.bereta.funds;

import java.time.Instant;
import java.util.UUID;

public record FundMembershipResponse(
        UUID id,
        UUID userId,
        String email,
        String phone,
        String displayName,
        FundRole role,
        UUID localFundId,
        String localFundName,
        Instant createdAt
) {
    static FundMembershipResponse from(FundMembershipEntity membership) {
        return new FundMembershipResponse(
                membership.getId(),
                membership.getUserAccount().getId(),
                membership.getUserAccount().getEmail(),
                membership.getUserAccount().getPhoneE164(),
                membership.getUserAccount().getDisplayName(),
                membership.getRole(),
                membership.getLocalFund() == null ? null : membership.getLocalFund().getId(),
                membership.getLocalFund() == null ? null : membership.getLocalFund().getName(),
                membership.getCreatedAt()
        );
    }
}
