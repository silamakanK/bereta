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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paiements et suivi des cotisations. Toutes les lectures partent de l'association active
 * ({@link FundScope}) : le responsable voit tout, caisse principale comprise ; le trésorier, ses caisses.
 */
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
        FundScope scope = fundAccessService.scope(userId);
        MemberCampaignEntity enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .filter(found -> found.getAssociation().getId().equals(scope.associationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscription introuvable"));
        if (!scope.covers(enrollment.getLocalFund())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette inscription n’est pas suivie par votre caisse.");
        }
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
        // La caisse qui encaisse est figée dans le paiement (celle de l'inscription, à cet instant).
        PaymentEntity payment = new PaymentEntity(enrollment, enrollment.getLocalFund(), request.amount(),
                request.paidOn(), request.paymentMethod(), normalizeNote(request.note()), recorder);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    public List<PaymentResponse> history(UUID userId) {
        FundScope scope = fundAccessService.scope(userId);
        return paymentRepository.findAllForAssociation(scope.associationId()).stream()
                .filter(payment -> scope.covers(payment.getLocalFund()))
                .map(PaymentResponse::from).toList();
    }

    public List<PaymentResponse> historyForMember(UUID userId, UUID memberId) {
        memberService.requireAccessibleMember(userId, memberId);
        FundScope scope = fundAccessService.scope(userId);
        return paymentRepository.findAllForMemberInAssociation(memberId, scope.associationId()).stream()
                .filter(payment -> scope.covers(payment.getLocalFund()))
                .map(PaymentResponse::from).toList();
    }

    public List<PaymentEnrollmentResponse> payableEnrollments(UUID userId) {
        FundScope scope = fundAccessService.scope(userId);
        Map<UUID, BigDecimal> paid = paidAmountsByEnrollment(scope.associationId());
        LocalDate today = LocalDate.now();
        return visibleEnrollments(scope).stream()
                .filter(enrollment -> enrollment.getExpectedAmount() != null)
                .map(enrollment -> PaymentEnrollmentResponse.from(enrollment,
                        paid.getOrDefault(enrollment.getId(), BigDecimal.ZERO), today))
                .filter(enrollment -> enrollment.remainingAmount() != null && enrollment.remainingAmount().signum() > 0)
                .sorted(Comparator.comparing(PaymentEnrollmentResponse::memberName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PaymentSummaryResponse summary(UUID userId) {
        FundScope scope = fundAccessService.scope(userId);
        List<MemberCampaignEntity> enrollments = visibleEnrollments(scope);
        Map<UUID, BigDecimal> paidByEnrollment = paidAmountsByEnrollment(scope.associationId());
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

    /**
     * Tableau de suivi : chaque membre visible, sa caisse et ses inscriptions (dû, payé, reste, statut).
     * Les filtres, le tri et la pagination se font côté navigateur.
     */
    public List<MemberTrackingResponse> tracking(UUID userId) {
        FundScope scope = fundAccessService.scope(userId);
        Map<UUID, BigDecimal> paid = paidAmountsByEnrollment(scope.associationId());
        LocalDate today = LocalDate.now();
        Map<UUID, List<PaymentEnrollmentResponse>> byMember = visibleEnrollments(scope).stream()
                .map(enrollment -> PaymentEnrollmentResponse.from(enrollment,
                        paid.getOrDefault(enrollment.getId(), BigDecimal.ZERO), today))
                .collect(Collectors.groupingBy(PaymentEnrollmentResponse::memberId));
        return memberService.visibleMembers(userId).stream()
                .map(member -> MemberTrackingResponse.from(member, byMember.getOrDefault(member.getId(), List.of())))
                .toList();
    }

    private List<MemberCampaignEntity> visibleEnrollments(FundScope scope) {
        return enrollmentRepository.findAllForAssociation(scope.associationId()).stream()
                .filter(enrollment -> scope.covers(enrollment.getLocalFund()))
                .toList();
    }

    private Map<UUID, BigDecimal> paidAmountsByEnrollment(UUID associationId) {
        return paymentRepository.sumByEnrollmentForAssociation(associationId).stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> (BigDecimal) row[1], BigDecimal::add));
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        return note.trim();
    }

    /** Libellé d'une caisse pour l'affichage : nom de la caisse locale, ou « Caisse principale ». */
    static String fundLabel(LocalFundEntity localFund) {
        return localFund == null ? "Caisse principale" : localFund.getName();
    }

    static UUID fundId(LocalFundEntity localFund) {
        return localFund == null ? null : localFund.getId();
    }
}
