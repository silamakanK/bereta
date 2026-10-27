package com.bereta.funds;

import java.time.Instant;
import java.util.UUID;

public record LocalFundResponse(UUID id, UUID primaryFundId, String primaryFundName, String name, Instant createdAt) {
    static LocalFundResponse from(LocalFundEntity fund) {
        return new LocalFundResponse(fund.getId(), fund.getPrimaryFund().getId(),
                fund.getPrimaryFund().getName(), fund.getName(), fund.getCreatedAt());
    }
}
