package com.bereta.funds;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.members.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberCampaignRepository enrollmentRepository;
    private final FundAccessService fundAccessService;
    private final UserAccountRepository userAccountRepository;
    private final MemberService memberService;

    public PaymentService(PaymentRepository paymentRepository,
                          MemberCampaignRepository enrollmentRepository,
                          FundAccessService fundAccessService,
                          UserAccountRepository userAccountRepository,
                          MemberService memberService) {
        this.paymentRepository = paymentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.fundAccessService = fundAccessService;
        this.userAccountRepository = userAccountRepository;
        this.memberService = memberService;
    }

    @Transactional
    public PaymentResponse record(UUID userId, UUID enrollmentId, PaymentRequest request) {
        MemberCampaignEntity enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscription introuvable"));
        UUID primaryFundId = enrollment.getCampaign().getPrimaryFund().getId();
        fundAccessService.requireLocalAccess(userId, primaryFundId, enrollment.getLocalFund().getId());

        if (enrollment.getExpectedAmount() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Définissez le montant attendu de la campagne avant d’enregistrer un paiement.");
        }
        BigDecimal alreadyPaid = paymentRepository.sumForEnrollment(enrollmentId);
        BigDecimal remaining = enrollment.getExpectedAmount().subtract(alreadyPaid).max(BigDecimal.ZERO);
        if (request.amount().compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le versement dépasse le reste à payer (" + remaining.toPlainString() + " €).");
        }
        UserAccountEntity recorder = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        PaymentEntity payment = new PaymentEntity(enrollment, enrollment.getLocalFund(), request.amount(),
                request.paidOn(), request.paymentMethod(), normalizeNote(request.note()), recorder);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    public List<PaymentResponse> history(UUID userId) {
        List<UUID> localFundIds = fundAccessService.accessibleLocalFundIds(userId);
        if (localFundIds.isEmpty()) return List.of();
        return paymentRepository.findAllForHistory(localFundIds).stream()
                .filter(payment -> fundAccessService.hasLocalAccess(userId,
                        payment.getMemberCampaign().getCampaign().getPrimaryFund().getId(),
                        payment.getLocalFund().getId()))
                .map(PaymentResponse::from).toList();
    }

    public List<PaymentResponse> historyForMember(UUID userId, UUID memberId) {
        memberService.requireAccessibleMember(userId, memberId);
        List<UUID> localFundIds = fundAccessService.accessibleLocalFundIds(userId);
        if (localFundIds.isEmpty()) return List.of();
        return paymentRepository.findAllForMember(memberId, localFundIds).stream()
                .filter(payment -> fundAccessService.hasLocalAccess(userId,
                        payment.getMemberCampaign().getCampaign().getPrimaryFund().getId(),
                        payment.getLocalFund().getId()))
                .map(PaymentResponse::from).toList();
    }

    public List<PaymentEnrollmentResponse> payableEnrollments(UUID userId) {
        List<UUID> accessibleLocalIds = fundAccessService.accessibleLocalFundIds(userId);
        if (accessibleLocalIds.isEmpty()) return List.of();
        Map<UUID, BigDecimal> paidByEnrollment = paidAmountsByEnrollment(accessibleLocalIds);
        return enrollmentRepository.findDistinctByLocalFundIdIn(accessibleLocalIds).stream()
                .filter(enrollment -> enrollment.getExpectedAmount() != null)
                .filter(enrollment -> fundAccessService.hasLocalAccess(userId,
                        enrollment.getCampaign().getPrimaryFund().getId(), enrollment.getLocalFund().getId()))
                .map(enrollment -> PaymentEnrollmentResponse.from(enrollment,
                        paidByEnrollment.getOrDefault(enrollment.getId(), BigDecimal.ZERO), LocalDate.now()))
                .filter(enrollment -> enrollment.remainingAmount() != null && enrollment.remainingAmount().signum() > 0)
                .sorted(Comparator.comparing(PaymentEnrollmentResponse::memberName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PaymentSummaryResponse summary(UUID userId) {
        List<UUID> localFundIds = fundAccessService.accessibleLocalFundIds(userId);
        if (localFundIds.isEmpty()) {
            return new PaymentSummaryResponse(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Collection<UUID> localIds = localFundIds;
        List<MemberCampaignEntity> enrollments = enrollmentRepository.findDistinctByLocalFundIdIn(localIds).stream()
                .filter(enrollment -> fundAccessService.hasLocalAccess(userId,
                        enrollment.getCampaign().getPrimaryFund().getId(), enrollment.getLocalFund().getId()))
                .toList();
        Map<UUID, BigDecimal> paidByEnrollment = paidAmountsByEnrollment(localIds);
        List<MemberCampaignEntity> valued = enrollments.stream().filter(e -> e.getExpectedAmount() != null).toList();
        BigDecimal expected = valued.stream().map(MemberCampaignEntity::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = enrollments.stream().map(e -> paidByEnrollment.getOrDefault(e.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = valued.stream().map(e -> e.getExpectedAmount()
                        .subtract(paidByEnrollment.getOrDefault(e.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long members = enrollments.stream().map(e -> e.getMember().getId()).distinct().count();
        return new PaymentSummaryResponse(members, expected, collected, remaining);
    }

    private Map<UUID, BigDecimal> paidAmountsByEnrollment(Collection<UUID> localFundIds) {
        if (localFundIds.isEmpty()) return Map.of();
        return paymentRepository.sumByEnrollment(localFundIds).stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> (BigDecimal) row[1], BigDecimal::add));
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        return note.trim();
    }
}
