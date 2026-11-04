package com.bereta.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CashRemittanceResponse(
        UUID id,
        UUID primaryFundId,
        String primaryFundName,
        UUID localFundId,
        String localFundName,
        BigDecimal amount,
        LocalDate transferredOn,
        PaymentMethod method,
        String note,
        RemittanceStatus status,
        String declaredByName,
        Instant declaredAt,
        String confirmedByName,
        Instant confirmedAt,
        String cancelledByName,
        Instant cancelledAt,
        String cancellationReason
) {
    static CashRemittanceResponse from(CashRemittanceEntity remittance) {
        return new CashRemittanceResponse(remittance.getId(), remittance.getPrimaryFund().getId(),
                remittance.getPrimaryFund().getName(), remittance.getLocalFund().getId(),
                remittance.getLocalFund().getName(), remittance.getAmount(), remittance.getTransferredOn(),
                remittance.getMethod(), remittance.getNote(), remittance.getStatus(),
                remittance.getDeclaredBy().getDisplayName(), remittance.getDeclaredAt(),
                remittance.getConfirmedBy() == null ? null : remittance.getConfirmedBy().getDisplayName(),
                remittance.getConfirmedAt(),
                remittance.getCancelledBy() == null ? null : remittance.getCancelledBy().getDisplayName(),
                remittance.getCancelledAt(), remittance.getCancellationReason());
    }
}
