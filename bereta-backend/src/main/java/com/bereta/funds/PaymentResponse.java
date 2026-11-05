package com.bereta.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID enrollmentId,
        UUID memberId,
        String memberName,
        UUID campaignId,
        String campaignName,
        /** Caisse qui a encaissé (null = caisse principale), figée au moment du paiement. */
        UUID localFundId,
        String localFundName,
        BigDecimal amount,
        LocalDate paidOn,
        PaymentMethod paymentMethod,
        String note,
        String recordedByName,
        Instant createdAt
) {
    static PaymentResponse from(PaymentEntity payment) {
        MemberCampaignEntity enrollment = payment.getMemberCampaign();
        return new PaymentResponse(payment.getId(), enrollment.getId(), enrollment.getMember().getId(),
                enrollment.getMember().getFirstName() + " " + enrollment.getMember().getLastName(),
                enrollment.getCampaign().getId(), enrollment.getCampaign().getName(),
                PaymentService.fundId(payment.getLocalFund()), PaymentService.fundLabel(payment.getLocalFund()), payment.getAmount(), payment.getPaidOn(),
                payment.getPaymentMethod(), payment.getNote(), payment.getRecordedBy().getDisplayName(),
                payment.getCreatedAt());
    }
}
