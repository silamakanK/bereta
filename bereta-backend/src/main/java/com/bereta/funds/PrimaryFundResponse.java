package com.bereta.funds;

import java.time.Instant;
import java.util.UUID;

public record PrimaryFundResponse(UUID id, String name, String objective, String description, Instant createdAt) {
    static PrimaryFundResponse from(PrimaryFundEntity fund) {
        return new PrimaryFundResponse(fund.getId(), fund.getName(), fund.getObjective(),
                fund.getDescription(), fund.getCreatedAt());
    }
}
