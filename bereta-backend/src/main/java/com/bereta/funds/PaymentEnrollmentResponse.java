package com.bereta.funds;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Inscription d'un membre à une campagne avec son état de paiement. */
public record PaymentEnrollmentResponse(
        UUID id,
        UUID memberId,
        String memberName,
        UUID campaignId,
        String campaignName,
        Integer campaignYear,
        String campaignStatus,
        LocalDate dueDate,
        /** null = suivi par la caisse principale. */
        UUID localFundId,
        String localFundName,
        BigDecimal expectedAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        String status
) {
    static PaymentEnrollmentResponse from(MemberCampaignEntity enrollment, BigDecimal paidAmount, LocalDate today) {
        CampaignEntity campaign = enrollment.getCampaign();
        BigDecimal expected = enrollment.getExpectedAmount();
        BigDecimal remaining = expected == null ? null : expected.subtract(paidAmount).max(BigDecimal.ZERO);
        return new PaymentEnrollmentResponse(enrollment.getId(), enrollment.getMember().getId(),
                enrollment.getMember().getFirstName() + " " + enrollment.getMember().getLastName(),
                campaign.getId(), campaign.getName(), campaign.getCampaignYear(), campaign.getStatus(),
                campaign.getDueDate(), PaymentService.fundId(enrollment.getLocalFund()),
                PaymentService.fundLabel(enrollment.getLocalFund()), expected, paidAmount, remaining,
                status(expected, paidAmount, campaign.getDueDate(), today));
    }

    /** PAYE, PARTIELLEMENT_PAYE, A_PAYER, EN_RETARD (échéance passée et reste dû) ou MONTANT_A_DEFINIR. */
    static String status(BigDecimal expected, BigDecimal paid, LocalDate dueDate, LocalDate today) {
        if (expected == null) return "MONTANT_A_DEFINIR";
        if (expected.subtract(paid).signum() <= 0) return "PAYE";
        if (dueDate != null && dueDate.isBefore(today)) return "EN_RETARD";
        return paid.signum() > 0 ? "PARTIELLEMENT_PAYE" : "A_PAYER";
    }
}
