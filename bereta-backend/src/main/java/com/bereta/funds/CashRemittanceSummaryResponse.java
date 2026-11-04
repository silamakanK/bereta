package com.bereta.funds;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CashRemittanceSummaryResponse(
        UUID primaryFundId,
        String primaryFundName,
        List<LocalFundCashBalanceResponse> localFunds,
        BigDecimal totalCollected,
        BigDecimal availableLocally,
        BigDecimal inTransit,
        BigDecimal centralBalance,
        BigDecimal totalHeldGlobally
) {
}
