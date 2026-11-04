package com.bereta.funds;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationRequestContext;
import com.bereta.associations.AssociationAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PrimaryFundService {

    private final PrimaryFundRepository primaryFundRepository;
    private final LocalFundRepository localFundRepository;
    private final UserAccountRepository userAccountRepository;
    private final FundAccessService fundAccessService;
    private final AssociationRequestContext associationContext;
    private final AssociationAccessService associationAccessService;

    public PrimaryFundService(PrimaryFundRepository primaryFundRepository, LocalFundRepository localFundRepository,
                              UserAccountRepository userAccountRepository, FundAccessService fundAccessService,
                              AssociationRequestContext associationContext, AssociationAccessService associationAccessService) {
        this.primaryFundRepository = primaryFundRepository;
        this.localFundRepository = localFundRepository;
        this.userAccountRepository = userAccountRepository;
        this.fundAccessService = fundAccessService;
        this.associationContext = associationContext;
        this.associationAccessService = associationAccessService;
    }

    public List<PrimaryFundResponse> findAll(UUID userId) {
        List<UUID> ids = fundAccessService.managerPrimaryFundIds(userId);
        if (ids.isEmpty()) ids = fundAccessService.accessiblePrimaryFundIds(userId);
        return primaryFundRepository.findAllById(ids).stream()
                .map(PrimaryFundResponse::from).toList();
    }

    public PrimaryFundResponse findById(UUID userId, UUID id) {
        fundAccessService.requireAccess(userId, id);
        return PrimaryFundResponse.from(requirePrimaryFund(id));
    }

    @Transactional
    public PrimaryFundResponse create(UUID userId, PrimaryFundRequest request) {
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        var association = associationAccessService.requireManager(userId, associationContext.requireId());
        PrimaryFundEntity fund = new PrimaryFundEntity(association,
                request.name().trim(), request.objective().trim(), description);
        userAccountRepository.findById(userId).filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        return PrimaryFundResponse.from(primaryFundRepository.save(fund));
    }

    public List<LocalFundResponse> findLocalFunds(UUID userId, UUID primaryFundId) {
        FundMembershipEntity membership = fundAccessService.requireAccess(userId, primaryFundId);
        List<LocalFundEntity> funds = fundAccessService.isAssociationManager(userId, associationContext.requireId())
                ? localFundRepository.findAllByPrimaryFundIdOrderByNameAsc(primaryFundId)
                : List.of(membership.getLocalFund());
        return funds.stream()
                .map(LocalFundResponse::from).toList();
    }

    @Transactional
    public LocalFundResponse findLocalFund(UUID userId, UUID primaryFundId, UUID localFundId) {
        return LocalFundResponse.from(fundAccessService.requireLocalAccess(userId, primaryFundId, localFundId));
    }

    @Transactional
    public LocalFundResponse createLocalFund(UUID userId, UUID primaryFundId, LocalFundRequest request) {
        fundAccessService.requireManager(userId, primaryFundId);
        PrimaryFundEntity primaryFund = requirePrimaryFund(primaryFundId);
        LocalFundEntity localFund = new LocalFundEntity(primaryFund, request.name().trim());
        return LocalFundResponse.from(localFundRepository.save(localFund));
    }

    private PrimaryFundEntity requirePrimaryFund(UUID id) {
        return primaryFundRepository.findById(id).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Caisse principale introuvable"));
    }
}
