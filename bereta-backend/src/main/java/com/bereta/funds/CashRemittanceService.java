package com.bereta.funds;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CashRemittanceService {

    private static final List<RemittanceStatus> ACTIVE_BALANCE_STATUSES =
            List.of(RemittanceStatus.EN_ATTENTE, RemittanceStatus.CONFIRMEE);

    private final CashRemittanceRepository remittanceRepository;
    private final LocalFundRepository localFundRepository;
    private final PrimaryFundRepository primaryFundRepository;
    private final PaymentRepository paymentRepository;
    private final UserAccountRepository userAccountRepository;
    private final FundAccessService fundAccessService;

    public CashRemittanceService(CashRemittanceRepository remittanceRepository,
                                 LocalFundRepository localFundRepository,
                                 PrimaryFundRepository primaryFundRepository,
                                 PaymentRepository paymentRepository,
                                 UserAccountRepository userAccountRepository,
                                 FundAccessService fundAccessService) {
        this.remittanceRepository = remittanceRepository;
        this.localFundRepository = localFundRepository;
        this.primaryFundRepository = primaryFundRepository;
        this.paymentRepository = paymentRepository;
        this.userAccountRepository = userAccountRepository;
        this.fundAccessService = fundAccessService;
    }

    @Transactional
    public CashRemittanceResponse declare(UUID userId, UUID localFundId, RemittanceRequest request) {
        LocalFundEntity localFund = localFundRepository.findByIdForUpdate(localFundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
        PrimaryFundEntity primaryFund = localFund.getPrimaryFund();
        fundAccessService.requireTreasurer(userId, primaryFund.getId(), localFundId);

        BigDecimal collected = paymentRepository.sumForLocalFunds(List.of(localFundId));
        BigDecimal alreadyCommitted = remittanceRepository.sumForLocalFundAndStatuses(
                localFundId, ACTIVE_BALANCE_STATUSES);
        BigDecimal available = collected.subtract(alreadyCommitted).max(BigDecimal.ZERO);
        if (request.amount().compareTo(available) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le montant dépasse l’argent disponible dans cette caisse (" + available.toPlainString() + " €).");
        }

        UserAccountEntity declaringUser = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        CashRemittanceEntity remittance = new CashRemittanceEntity(primaryFund, localFund, request.amount(),
                request.transferredOn(), request.method(), normalize(request.note()), declaringUser);
        return CashRemittanceResponse.from(remittanceRepository.save(remittance));
    }

    @Transactional
    public CashRemittanceResponse confirm(UUID managerId, UUID primaryFundId, UUID remittanceId) {
        fundAccessService.requireManager(managerId, primaryFundId);
        CashRemittanceEntity remittance = requireRemittanceForUpdate(primaryFundId, remittanceId);
        requirePending(remittance);
        UserAccountEntity manager = userAccountRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        remittance.confirm(manager);
        return CashRemittanceResponse.from(remittance);
    }

    @Transactional
    public CashRemittanceResponse cancel(UUID managerId, UUID primaryFundId, UUID remittanceId, String reason) {
        fundAccessService.requireManager(managerId, primaryFundId);
        CashRemittanceEntity remittance = requireRemittanceForUpdate(primaryFundId, remittanceId);
        requirePending(remittance);
        UserAccountEntity manager = userAccountRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        remittance.cancel(manager, reason.trim());
        return CashRemittanceResponse.from(remittance);
    }

    public List<CashRemittanceResponse> list(UUID userId, UUID primaryFundId) {
        FundMembershipEntity membership = fundAccessService.requireAccess(userId, primaryFundId);
        UUID associationId = membership == null
                ? primaryFundRepository.findById(primaryFundId).orElseThrow().getAssociation().getId()
                : membership.getAssociation().getId();
        List<UUID> localFundIds = fundAccessService.isAssociationManager(userId, associationId)
                ? localFundRepository.findAllByPrimaryFundIdOrderByNameAsc(primaryFundId).stream()
                    .map(LocalFundEntity::getId).toList()
                : List.of(membership.getLocalFund().getId());
        if (localFundIds.isEmpty()) return List.of();
        return remittanceRepository.findAllForLocalFunds(localFundIds).stream()
                .filter(remittance -> remittance.getPrimaryFund().getId().equals(primaryFundId))
                .map(CashRemittanceResponse::from).toList();
    }

    public LocalFundCashBalanceResponse localBalance(UUID userId, UUID localFundId) {
        LocalFundEntity localFund = localFundRepository.findById(localFundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
        fundAccessService.requireLocalAccess(userId, localFund.getPrimaryFund().getId(), localFundId);
        return localBalance(localFund, paymentRepository.sumForLocalFunds(List.of(localFundId)),
                remittanceAmounts(List.of(localFundId)).getOrDefault(localFundId, Map.of()));
    }

    public CashRemittanceSummaryResponse summary(UUID userId, UUID primaryFundId) {
        FundMembershipEntity membership = fundAccessService.requireAccess(userId, primaryFundId);
        PrimaryFundEntity primaryFund = primaryFundRepository.findById(primaryFundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse principale introuvable"));
        boolean manager = fundAccessService.isAssociationManager(userId, primaryFund.getAssociation().getId());
        List<LocalFundEntity> localFunds = manager
                ? localFundRepository.findAllByPrimaryFundIdOrderByNameAsc(primaryFundId)
                : List.of(membership.getLocalFund());
        List<UUID> localFundIds = localFunds.stream().map(LocalFundEntity::getId).toList();
        Map<UUID, BigDecimal> collectedByLocal = collectedAmounts(localFundIds);
        Map<UUID, Map<RemittanceStatus, BigDecimal>> remittancesByLocal = remittanceAmounts(localFundIds);
        List<LocalFundCashBalanceResponse> localBalances = localFunds.stream()
                .map(local -> localBalance(local, collectedByLocal.getOrDefault(local.getId(), BigDecimal.ZERO),
                        remittancesByLocal.getOrDefault(local.getId(), Map.of())))
                .toList();
        BigDecimal totalCollected = sum(localBalances, LocalFundCashBalanceResponse::totalCollected);
        BigDecimal available = sum(localBalances, LocalFundCashBalanceResponse::availableLocally);
        BigDecimal inTransit = sum(localBalances, LocalFundCashBalanceResponse::inTransit);
        // Un paiement encaissé par la caisse principale compte directement dans le solde central.
        BigDecimal direct = manager ? paymentRepository.sumDirectForAssociation(primaryFund.getAssociation().getId()) : null;
        BigDecimal central = manager
                ? remittanceRepository.sumForPrimaryFundAndStatus(primaryFundId, RemittanceStatus.CONFIRMEE).add(direct)
                : null;
        BigDecimal global = manager ? available.add(inTransit).add(central) : null;
        return new CashRemittanceSummaryResponse(primaryFundId, primaryFund.getName(), localBalances,
                manager ? totalCollected.add(direct) : totalCollected, available, inTransit, central, direct, global);
    }

    private CashRemittanceEntity requireRemittanceForUpdate(UUID primaryFundId, UUID remittanceId) {
        return remittanceRepository.findByIdForUpdate(remittanceId)
                .filter(remittance -> remittance.getPrimaryFund().getId().equals(primaryFundId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remise introuvable"));
    }

    private void requirePending(CashRemittanceEntity remittance) {
        if (remittance.getStatus() != RemittanceStatus.EN_ATTENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette remise n’est plus en attente et ne peut plus être confirmée ou annulée.");
        }
    }

    private LocalFundCashBalanceResponse localBalance(LocalFundEntity localFund, BigDecimal collected,
                                                      Map<RemittanceStatus, BigDecimal> amounts) {
        BigDecimal pending = amounts.getOrDefault(RemittanceStatus.EN_ATTENTE, BigDecimal.ZERO);
        BigDecimal confirmed = amounts.getOrDefault(RemittanceStatus.CONFIRMEE, BigDecimal.ZERO);
        return new LocalFundCashBalanceResponse(localFund.getId(), localFund.getName(), collected,
                collected.subtract(pending).subtract(confirmed).max(BigDecimal.ZERO), pending);
    }

    private Map<UUID, BigDecimal> collectedAmounts(Collection<UUID> localFundIds) {
        if (localFundIds.isEmpty()) return Map.of();
        return paymentRepository.sumByLocalFund(localFundIds).stream().collect(Collectors.toMap(
                row -> (UUID) row[0], row -> (BigDecimal) row[1]));
    }

    private Map<UUID, Map<RemittanceStatus, BigDecimal>> remittanceAmounts(Collection<UUID> localFundIds) {
        if (localFundIds.isEmpty()) return Map.of();
        Map<UUID, Map<RemittanceStatus, BigDecimal>> result = new java.util.HashMap<>();
        for (Object[] row : remittanceRepository.sumByLocalFundAndStatus(localFundIds, ACTIVE_BALANCE_STATUSES)) {
            result.computeIfAbsent((UUID) row[0], ignored -> new EnumMap<>(RemittanceStatus.class))
                    .put((RemittanceStatus) row[1], (BigDecimal) row[2]);
        }
        return result;
    }

    private <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalize(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
