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
        /** Remises confirmées + paiements encaissés directement par la caisse principale (responsable). */
        BigDecimal centralBalance,
        /** Part du solde central encaissée directement par la caisse principale (responsable). */
        BigDecimal collectedCentrally,
        BigDecimal totalHeldGlobally
) {
}
