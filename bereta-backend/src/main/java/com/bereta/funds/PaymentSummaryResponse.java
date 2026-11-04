package com.bereta.funds;

import java.math.BigDecimal;

public record PaymentSummaryResponse(
        long memberCount,
        BigDecimal expectedAmount,
        BigDecimal collectedAmount,
        BigDecimal remainingAmount
) {
}
