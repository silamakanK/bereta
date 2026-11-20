package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.associations.AssociationMembershipRepository;
import com.bereta.associations.AssociationRepository;
import com.bereta.associations.AssociationStatus;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.members.MemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Vue d'ensemble et associations (consultation, archivage motivé). Les comptes sont dans {@link AdminUserService} ;
 * la création ou la promotion d'un administrateur n'est possible que par la procédure interne
 * (docs/admin/promouvoir-admin.sql), jamais par l'API.
 */
@Service
@Transactional(readOnly = true)
public class AdminService {

    private final UserAccountRepository users;
    private final AssociationRepository associations;
    private final AssociationMembershipRepository associationMemberships;
    private final PrimaryFundRepository primaryFunds;
    private final LocalFundRepository localFunds;
    private final MemberRepository members;
    private final AdminAuditService auditService;

    public AdminService(UserAccountRepository users, AssociationRepository associations,
                        AssociationMembershipRepository associationMemberships,
                        PrimaryFundRepository primaryFunds, LocalFundRepository localFunds,
                        MemberRepository members, AdminAuditService auditService) {
        this.users = users;
        this.associations = associations;
        this.associationMemberships = associationMemberships;
        this.primaryFunds = primaryFunds;
        this.localFunds = localFunds;
        this.members = members;
        this.auditService = auditService;
    }

    public AdminOverviewResponse overview() {
        long primaryCount = primaryFunds.count();
        long localCount = localFunds.count();
        return new AdminOverviewResponse(associations.count(), associations.countByStatus(AssociationStatus.ACTIVE),
                users.count(), users.countByEnabledTrue(), primaryCount, localCount, primaryCount + localCount,
                members.count(), users.countByRoleAndEnabledTrue("ADMIN"));
    }

    public AdminPageResponse<AdminAssociationResponse> listAssociations(String query, int page) {
        var result = associations.findAllByNameContainingIgnoreCase(query == null ? "" : query.trim(),
                PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        return AdminPageResponse.from(result, this::associationResponse);
    }

    public AdminAssociationResponse getAssociation(UUID id) {
        return associationResponse(requireAssociation(id));
    }

    @Transactional
    public AdminAssociationResponse archiveAssociation(UUID actorId, UUID id, String reason) {
        AssociationEntity association = requireAssociation(id);
        if (association.getStatus() == AssociationStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette association est déjà archivée.");
        }
        UserAccountEntity actor = users.findById(actorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte utilisateur introuvable."));
        association.archive();
        auditService.record(actor, new AdminAuditService.Entry(AdminAction.ASSOCIATION_ARCHIVED, "ASSOCIATION", id,
                association, reason, Map.of("status", "ACTIVE"), Map.of("status", "ARCHIVED")));
        return associationResponse(association);
    }

    private AdminAssociationResponse associationResponse(AssociationEntity association) {
        var responsibleEmails = associationMemberships
                .findAllByAssociationIdAndActiveTrueOrderByCreatedAtAsc(association.getId()).stream()
                .map(m -> m.getUserAccount().getEmail()).toList();
        return AdminAssociationResponse.from(association, primaryFunds.countByAssociationId(association.getId()),
                localFunds.countByAssociationId(association.getId()), members.countByAssociationId(association.getId()),
                responsibleEmails);
    }

    private AssociationEntity requireAssociation(UUID id) {
        return associations.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Association introuvable."));
    }
}
