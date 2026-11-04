package com.bereta.funds;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentEnrollmentResponse(
        UUID id,
        UUID memberId,
        String memberName,
        UUID campaignId,
        String campaignName,
        Integer campaignYear,
        UUID localFundId,
        String localFundName,
        BigDecimal expectedAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String status
) {
    static PaymentEnrollmentResponse from(MemberCampaignEntity enrollment, BigDecimal paidAmount, LocalDate today) {
        BigDecimal expected = enrollment.getExpectedAmount();
        BigDecimal remaining = expected == null ? null : expected.subtract(paidAmount).max(BigDecimal.ZERO);
        String status = expected == null ? "MONTANT_A_DEFINIR"
                : remaining.signum() == 0 ? "PAYE"
                : enrollment.getCampaign().getDueDate() != null && enrollment.getCampaign().getDueDate().isBefore(today)
                ? "EN_RETARD" : paidAmount.signum() > 0 ? "PARTIELLEMENT_PAYE" : "A_PAYER";
        return new PaymentEnrollmentResponse(enrollment.getId(), enrollment.getMember().getId(),
                enrollment.getMember().getFirstName() + " " + enrollment.getMember().getLastName(),
                enrollment.getCampaign().getId(), enrollment.getCampaign().getName(),
                enrollment.getCampaign().getCampaignYear(), enrollment.getLocalFund().getId(),
                enrollment.getLocalFund().getName(), expected, paidAmount, remaining, status);
    }
}
