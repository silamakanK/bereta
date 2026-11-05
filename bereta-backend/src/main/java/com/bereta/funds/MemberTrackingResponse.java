package com.bereta.funds;

import com.bereta.members.MemberEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ligne du tableau des membres : le membre, sa caisse et ses inscriptions aux campagnes. */
public record MemberTrackingResponse(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        /** null = rattaché directement à la caisse principale. */
        UUID localFundId,
        String fundName,
        boolean active,
        Instant createdAt,
        List<PaymentEnrollmentResponse> enrollments
) {
    static MemberTrackingResponse from(MemberEntity member, List<PaymentEnrollmentResponse> enrollments) {
        return new MemberTrackingResponse(member.getId(), member.getFirstName(), member.getLastName(), member.getPhone(),
                PaymentService.fundId(member.getCreatedInLocalFund()), PaymentService.fundLabel(member.getCreatedInLocalFund()),
                member.isActive(), member.getCreatedAt(), enrollments);
    }
}
