package com.bereta.funds;

import com.bereta.associations.AssociationAccessService;
import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRequestContext;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FundAccessService {
    private final FundMembershipRepository membershipRepository;
    private final UserAccountRepository userAccountRepository;
    private final PrimaryFundRepository primaryFundRepository;
    private final LocalFundRepository localFundRepository;
    private final AssociationAccessService associationAccess;
    private final AssociationMembershipRepository associationMemberships;
    private final AssociationRequestContext context;
    private final com.bereta.admin.AdminAuditService adminAudit;

    public FundAccessService(FundMembershipRepository membershipRepository, UserAccountRepository userAccountRepository,
                             PrimaryFundRepository primaryFundRepository, LocalFundRepository localFundRepository,
                             AssociationAccessService associationAccess,
                             AssociationMembershipRepository associationMemberships,
                             AssociationRequestContext context, com.bereta.admin.AdminAuditService adminAudit) {
        this.membershipRepository = membershipRepository;
        this.userAccountRepository = userAccountRepository;
        this.primaryFundRepository = primaryFundRepository;
        this.localFundRepository = localFundRepository;
        this.associationAccess = associationAccess;
        this.associationMemberships = associationMemberships;
        this.context = context;
        this.adminAudit = adminAudit;
    }

    public List<FundMembershipEntity> membershipsForUser(UUID userId) {
        return membershipRepository.findAllByUserAccountIdAndAssociationIdAndActiveTrueOrderByCreatedAtAsc(
                userId, context.requireId());
    }

    public FundMembershipEntity requireAccess(UUID userId, UUID primaryFundId) {
        PrimaryFundEntity fund = primaryFundRepository.findById(primaryFundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse principale introuvable."));
        UUID associationId = context.requireId();
        if (!fund.getAssociation().getId().equals(associationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse principale introuvable.");
        }
        associationAccess.requireAccess(userId, associationId);
        if (isAssociationManager(userId, associationId)) return null;
        return membershipRepository.findAllByUserAccountIdAndAssociationIdAndActiveTrueOrderByCreatedAtAsc(
                        userId, associationId).stream()
                .filter(m -> m.getPrimaryFund().getId().equals(primaryFundId)
                        && m.getRole() == FundRole.TRESORIER)
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous n’avez pas accès à cette caisse principale."));
    }

    public FundMembershipEntity requireManager(UUID userId, UUID primaryFundId) {
        requireAccess(userId, primaryFundId);
        if (!isAssociationManager(userId, context.requireId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul un responsable de l’association peut effectuer cette action.");
        }
        return null;
    }

    public boolean hasLocalAccess(UUID userId, UUID primaryFundId, UUID localFundId) {
        try {
            LocalFundEntity local = localFundRepository.findById(localFundId).orElse(null);
            if (local == null || !local.getPrimaryFund().getId().equals(primaryFundId)
                    || !local.getAssociation().getId().equals(context.requireId())) return false;
            if (isAssociationManager(userId, context.requireId())) return true;
            return membershipRepository.existsByUserAccountIdAndPrimaryFundIdAndLocalFundIdAndActiveTrue(
                    userId, primaryFundId, localFundId);
        } catch (org.springframework.web.server.ResponseStatusException ex) { return false; }
    }

    public boolean hasManagerAccess(UUID userId, UUID primaryFundId) {
        return primaryFundRepository.findById(primaryFundId)
                .filter(f -> f.getAssociation().getId().equals(context.requireId()))
                .filter(f -> isAssociationManager(userId, context.requireId())).isPresent();
    }

    public List<UUID> managerPrimaryFundIds(UUID userId) {
        UUID associationId = context.requireId();
        if (!isAssociationManager(userId, associationId)) return List.of();
        return primaryFundRepository.findAllByAssociationIdOrderByNameAsc(associationId).stream()
                .map(PrimaryFundEntity::getId).toList();
    }

    public List<UUID> accessiblePrimaryFundIds(UUID userId) {
        return membershipsForUser(userId).stream().map(m -> m.getPrimaryFund().getId()).distinct().toList();
    }

    public List<UUID> accessibleLocalFundIds(UUID userId) {
        UUID associationId = context.requireId();
        associationAccess.requireAccess(userId, associationId);
        if (isAssociationManager(userId, associationId)) {
            return localFundRepository.findAllByAssociationIdOrderByNameAsc(associationId).stream()
                    .map(LocalFundEntity::getId).toList();
        }
        return membershipsForUser(userId).stream().filter(m -> m.getRole() == FundRole.TRESORIER)
                .map(m -> m.getLocalFund().getId()).distinct().toList();
    }

    public boolean isAssociationManager(UUID userId, UUID associationId) {
        if (userAccountRepository.findById(userId).map(u -> "ADMIN".equals(u.getRole())).orElse(false)) return true;
        return associationMemberships.findByAssociationIdAndUserAccountIdAndActiveTrue(associationId, userId).isPresent();
    }

    public LocalFundEntity requireLocalAccess(UUID userId, UUID primaryFundId, UUID localFundId) {
        requireAccess(userId, primaryFundId);
        LocalFundEntity localFund = localFundRepository.findById(localFundId)
                .filter(f -> f.getPrimaryFund().getId().equals(primaryFundId))
                .filter(f -> f.getAssociation().getId().equals(context.requireId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable."));
        if (!isAssociationManager(userId, context.requireId()) && !membershipRepository
                .existsByUserAccountIdAndPrimaryFundIdAndLocalFundIdAndActiveTrue(userId, primaryFundId, localFundId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n’avez pas accès à cette caisse locale.");
        }
        return localFund;
    }

    public FundMembershipEntity requireTreasurer(UUID userId, UUID primaryFundId, UUID localFundId) {
        LocalFundEntity local = requireLocalAccess(userId, primaryFundId, localFundId);
        if (isAssociationManager(userId, local.getAssociation().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action réservée au trésorier local.");
        }
        return membershipRepository.findByUserAccountIdAndLocalFundId(userId, localFundId)
                .filter(FundMembershipEntity::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès trésorier requis."));
    }

    @Transactional
    public FundMembershipEntity createResponsible(UserAccountEntity user, PrimaryFundEntity primaryFund) {
        return membershipRepository.save(new FundMembershipEntity(user, primaryFund, null, FundRole.RESPONSABLE));
    }

    public List<FundMembershipResponse> listMembers(UUID userId, UUID primaryFundId) {
        requireManager(userId, primaryFundId);
        return membershipRepository.findAllByPrimaryFundIdAndActiveTrueOrderByRoleAscCreatedAtAsc(primaryFundId)
                .stream().map(FundMembershipResponse::from).toList();
    }

    @Transactional
    public void deactivateMembership(UUID managerId, UUID primaryFundId, UUID membershipId) {
        requireManager(managerId, primaryFundId);
        FundMembershipEntity membership = membershipRepository.findByIdAndPrimaryFundIdAndActiveTrue(membershipId, primaryFundId)
                .filter(m -> m.getAssociation().getId().equals(context.requireId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Accès introuvable."));
        if (membership.getRole() == FundRole.RESPONSABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet accès historique ne peut pas être désactivé ici.");
        }
        membership.deactivate();
        userAccountRepository.findById(managerId).ifPresent(manager -> adminAudit.record(manager, "TREASURER_REMOVED",
                "LOCAL_FUND", membership.getLocalFund() == null ? null : membership.getLocalFund().getId(),
                membership.getAssociation(), "Retiré : " + (membership.getUserAccount().getDisplayName() == null
                        ? "Sans nom" : membership.getUserAccount().getDisplayName())
                        + " [" + membership.getUserAccount().getId() + "]"));
    }
}
