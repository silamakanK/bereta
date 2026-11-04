package com.bereta.associations;

import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AssociationAccessService {
    private final AssociationRepository associations;
    private final AssociationMembershipRepository memberships;
    private final FundMembershipRepository fundMemberships;
    private final UserAccountRepository users;
    private final AssociationRequestContext requestContext;

    public AssociationAccessService(AssociationRepository associations,
                                    AssociationMembershipRepository memberships,
                                    FundMembershipRepository fundMemberships,
                                    UserAccountRepository users,
                                    AssociationRequestContext requestContext) {
        this.associations = associations;
        this.memberships = memberships;
        this.fundMemberships = fundMemberships;
        this.users = users;
        this.requestContext = requestContext;
    }

    public AssociationEntity requireAccess(UUID userId, UUID associationId) {
        AssociationEntity association = associations.findByIdAndStatus(associationId, AssociationStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Association introuvable."));
        if (users.findById(userId).map(u -> "ADMIN".equals(u.getRole())).orElse(false)) return association;
        boolean manager = memberships.findByAssociationIdAndUserAccountIdAndActiveTrue(associationId, userId).isPresent();
        boolean treasurer = fundMemberships.existsByUserAccountIdAndAssociationIdAndRoleAndActiveTrue(
                userId, associationId, FundRole.TRESORIER);
        if (!manager && !treasurer) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n’avez pas accès à cette association.");
        }
        return association;
    }

    public AssociationEntity requireManager(UUID userId, UUID associationId) {
        AssociationEntity association = requireAccess(userId, associationId);
        if (!users.findById(userId).map(u -> "ADMIN".equals(u.getRole())).orElse(false)
                && memberships.findByAssociationIdAndUserAccountIdAndActiveTrue(associationId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action réservée aux responsables de l’association.");
        }
        return association;
    }

    public List<AssociationResponse> listFor(UUID userId) {
        if (users.findById(userId).map(u -> "ADMIN".equals(u.getRole())).orElse(false)) {
            return associations.findAllByStatusOrderByNameAsc(AssociationStatus.ACTIVE).stream()
                    .map(a -> AssociationResponse.from(a, false)).toList();
        }
        LinkedHashMap<UUID, AssociationEntity> visible = new LinkedHashMap<>();
        memberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(userId)
                .stream().map(AssociationMembershipEntity::getAssociation)
                .filter(a -> a.getStatus() == AssociationStatus.ACTIVE).forEach(a -> visible.put(a.getId(), a));
        fundMemberships.findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(userId).stream()
                .filter(m -> m.getRole() == FundRole.TRESORIER)
                .map(m -> m.getAssociation())
                .filter(a -> a.getStatus() == AssociationStatus.ACTIVE).forEach(a -> visible.put(a.getId(), a));
        return visible.values().stream().map(a -> AssociationResponse.from(a,
                memberships.findByAssociationIdAndUserAccountIdAndActiveTrue(a.getId(), userId).isPresent())).toList();
    }

    @Transactional
    public AssociationResponse create(UUID userId, CreateAssociationRequest request) {
        var user = users.findById(userId).filter(com.bereta.auth.UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide."));
        AssociationEntity association = associations.save(new AssociationEntity(
                request.name().trim(), clean(request.description()), clean(request.contactEmail()),
                clean(request.contactPhone()), clean(request.address())));
        memberships.save(new AssociationMembershipEntity(association, user));
        return AssociationResponse.from(association, true);
    }

    public List<AssociationManagerResponse> listManagers(UUID userId, UUID associationId) {
        requireActivePathAssociation(associationId);
        requireManager(userId, associationId);
        return memberships.findAllByAssociationIdAndActiveTrueOrderByCreatedAtAsc(associationId).stream()
                .map(AssociationManagerResponse::from).toList();
    }

    @Transactional
    public AssociationManagerResponse addManager(UUID userId, UUID associationId,
                                                  AddAssociationResponsibleRequest request) {
        requireActivePathAssociation(associationId);
        requireManager(userId, associationId);
        String email = request.email().trim();
        var account = users.findByEmailIgnoreCase(email)
                .filter(com.bereta.auth.UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun compte actif ne correspond à cette adresse e-mail."));
        AssociationMembershipEntity membership = memberships.findByAssociationIdAndUserAccountId(
                        associationId, account.getId())
                .orElseGet(() -> new AssociationMembershipEntity(
                        associations.findById(associationId).orElseThrow(), account));
        membership.activate();
        return AssociationManagerResponse.from(memberships.save(membership));
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void requireActivePathAssociation(UUID associationId) {
        if (!requestContext.requireId().equals(associationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Association introuvable.");
        }
    }
}
