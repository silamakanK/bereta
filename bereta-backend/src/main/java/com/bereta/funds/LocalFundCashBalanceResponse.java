package com.bereta.funds;

import java.math.BigDecimal;
import java.util.UUID;

public record LocalFundCashBalanceResponse(
        UUID localFundId,
        String localFundName,
        BigDecimal totalCollected,
        BigDecimal availableLocally,
        BigDecimal inTransit
) {
}
