package com.bereta.funds;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** treasurers vide = caisse « Sans responsable ». */
public record LocalFundResponse(UUID id, UUID primaryFundId, String primaryFundName, String name, String location,
                                String description, List<TreasurerSummary> treasurers, Instant createdAt) {

    public record TreasurerSummary(UUID membershipId, UUID userId, String displayName, String phone, String email) {
    }

    static LocalFundResponse from(LocalFundEntity fund) {
        return from(fund, List.of());
    }

    static LocalFundResponse from(LocalFundEntity fund, List<FundMembershipEntity> treasurers) {
        return new LocalFundResponse(fund.getId(), fund.getPrimaryFund().getId(),
                fund.getPrimaryFund().getName(), fund.getName(), fund.getLocation(), fund.getDescription(),
                treasurers.stream().map(m -> new TreasurerSummary(m.getId(), m.getUserAccount().getId(),
                        m.getUserAccount().getDisplayName(), m.getUserAccount().getPhoneE164(),
                        m.getUserAccount().getEmail())).toList(),
                fund.getCreatedAt());
    }
}
