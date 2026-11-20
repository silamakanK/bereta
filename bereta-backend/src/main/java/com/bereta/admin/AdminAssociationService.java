package com.bereta.admin;

import com.bereta.admin.AdminDtos.AssociationDetail;
import com.bereta.admin.AdminDtos.AssociationSummary;
import com.bereta.admin.AdminDtos.TransferRequest;
import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationMembershipEntity;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationStatus;
import com.bereta.auth.UserAccountEntity;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Associations vues par l'administrateur : consultation, archivage (lecture seule pour ses utilisateurs),
 * restauration et transfert du responsable. Chaque action est verrouillée et journalisée dans sa transaction.
 */
@Service
@Transactional(readOnly = true)
public class AdminAssociationService {

    private static final String TARGET = "ASSOCIATION";
    private static final String STATUS = "status";

    private final AdminAssociationReader reader;
    private final AdminUserReader users;
    private final AssociationMembershipRepository associationMemberships;
    private final FundMembershipRepository fundMemberships;
    private final AdminAuditService audit;

    AdminAssociationService(AdminAssociationReader reader, AdminUserReader users,
                            AssociationMembershipRepository associationMemberships,
                            FundMembershipRepository fundMemberships, AdminAuditService audit) {
        this.reader = reader;
        this.users = users;
        this.associationMemberships = associationMemberships;
        this.fundMemberships = fundMemberships;
        this.audit = audit;
    }

    public AdminPageResponse<AssociationSummary> list(AdminAssociationQuery.Filter filter, int page, int size) {
        return reader.list(filter, page, size);
    }

    public AssociationDetail detail(UUID id) {
        return reader.detail(id);
    }

    /** Consultation et exports restent possibles pour ses utilisateurs ; plus aucune écriture. */
    @Transactional
    public AssociationDetail archive(UUID actorId, UUID id, String reason) {
        return changeStatus(actorId, id, reason, AssociationStatus.ARCHIVED, AdminAction.ASSOCIATION_ARCHIVED,
                "Cette association est déjà archivée.");
    }

    @Transactional
    public AssociationDetail restore(UUID actorId, UUID id, String reason) {
        return changeStatus(actorId, id, reason, AssociationStatus.ACTIVE, AdminAction.ASSOCIATION_RESTORED,
                "Cette association est déjà active.");
    }

    /** Le nouveau responsable (compte responsable actif) reçoit les droits ; l'ancien les perd. */
    @Transactional
    public AssociationDetail transfer(UUID actorId, UUID id, TransferRequest request) {
        UserAccountEntity actor = users.require(actorId);
        AssociationEntity association = reader.requireForUpdate(id);
        AssociationMembershipEntity previous = associationMemberships
                .findByAssociationIdAndUserAccountIdAndActiveTrue(id, request.fromUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ce compte n’est pas responsable de cette association."));
        if (request.fromUserId().equals(request.toUserId())) {
            throw conflict("Choisissez un autre compte que le responsable actuel.");
        }
        UserAccountEntity next = users.require(request.toUserId());
        requireEligibleResponsible(next, id);

        AssociationMembershipEntity granted = associationMemberships.findByAssociationIdAndUserAccountId(id, next.getId())
                .orElseGet(() -> new AssociationMembershipEntity(association, next));
        granted.activate();
        associationMemberships.save(granted);
        previous.deactivate();
        UserAccountEntity former = previous.getUserAccount();
        audit.record(actor, new AdminAuditService.Entry(AdminAction.ASSOCIATION_TRANSFERRED, TARGET, id, association,
                request.reason().trim(),
                Map.of("responsibleId", former.getId().toString(), "responsibleName", AdminFundFigures.label(former)),
                Map.of("responsibleId", next.getId().toString(), "responsibleName", AdminFundFigures.label(next))));
        return reader.detail(id);
    }

    private AssociationDetail changeStatus(UUID actorId, UUID id, String reason, AssociationStatus target,
                                           AdminAction action, String alreadyMessage) {
        UserAccountEntity actor = users.require(actorId);
        AssociationEntity association = reader.requireForUpdate(id);
        AssociationStatus before = association.getStatus();
        if (before == target) throw conflict(alreadyMessage);
        association.setStatus(target);
        audit.record(actor, new AdminAuditService.Entry(action, TARGET, id, association, reason,
                Map.of(STATUS, before.name()), Map.of(STATUS, target.name())));
        return reader.detail(id);
    }

    private void requireEligibleResponsible(UserAccountEntity account, UUID associationId) {
        if (!account.isEnabled()) throw conflict("Ce compte est désactivé : réactivez-le ou choisissez-en un autre.");
        if (!"RESPONSABLE".equals(account.getRole())) {
            throw conflict("Seul un compte responsable peut recevoir une association.");
        }
        if (associationMemberships.findByAssociationIdAndUserAccountIdAndActiveTrue(associationId, account.getId()).isPresent()) {
            throw conflict("Ce compte est déjà responsable de cette association.");
        }
        if (fundMemberships.existsByUserAccountIdAndAssociationIdAndRoleAndActiveTrue(
                account.getId(), associationId, FundRole.TRESORIER)) {
            throw conflict("Ce compte est trésorier dans cette association : retirez-le d’abord de sa caisse.");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
