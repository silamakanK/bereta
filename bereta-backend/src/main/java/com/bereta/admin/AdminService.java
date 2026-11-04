package com.bereta.admin;

import com.bereta.associations.*;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.members.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AdminService {
    private final UserAccountRepository users;
    private final AssociationRepository associations;
    private final AssociationMembershipRepository associationMemberships;
    private final PrimaryFundRepository primaryFunds;
    private final LocalFundRepository localFunds;
    private final MemberRepository members;
    private final AdminAuditRepository audit;
    private final AdminAuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserAccountRepository users, AssociationRepository associations,
                        AssociationMembershipRepository associationMemberships,
                        PrimaryFundRepository primaryFunds, LocalFundRepository localFunds,
                        MemberRepository members, AdminAuditRepository audit, AdminAuditService auditService,
                        PasswordEncoder passwordEncoder) {
        this.users = users;
        this.associations = associations;
        this.associationMemberships = associationMemberships;
        this.primaryFunds = primaryFunds;
        this.localFunds = localFunds;
        this.members = members;
        this.audit = audit;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminOverviewResponse overview() {
        long primaryCount = primaryFunds.count();
        long localCount = localFunds.count();
        return new AdminOverviewResponse(associations.count(), associations.countByStatus(AssociationStatus.ACTIVE),
                users.count(), users.countByEnabledTrue(), primaryCount, localCount, primaryCount + localCount,
                members.count(), users.countByRoleAndEnabledTrue("ADMIN"));
    }

    public AdminPageResponse<AdminUserResponse> listUsers(String query, int page) {
        var result = users.searchForAdmin(query == null ? "" : query.trim(), page(page));
        return AdminPageResponse.from(result, AdminUserResponse::from);
    }

    public AdminUserResponse getUser(UUID id) { return AdminUserResponse.from(requireUser(id)); }

    @Transactional
    public AdminUserResponse createUser(UUID actorId, AdminUserCreateRequest request) {
        String email = normalizeEmail(request.email());
        validateRole(request.role(), false);
        validatePassword(request.password());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
        UserAccountEntity actor = requireUser(actorId);
        UserAccountEntity created;
        try {
            created = users.saveAndFlush(new UserAccountEntity(email, request.displayName().trim(),
                    passwordEncoder.encode(request.password()), request.role()));
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
        auditService.record(actor, "USER_CREATED", "USER", created.getId(), null,
                "Compte créé avec le rôle " + created.getRole());
        return AdminUserResponse.from(created);
    }

    @Transactional
    public AdminUserResponse updateUser(UUID actorId, UUID id, AdminUserUpdateRequest request) {
        UserAccountEntity target = requireUser(id);
        validateRole(request.role(), "ADMIN".equals(target.getRole()));
        boolean demotingAdmin = "ADMIN".equals(target.getRole()) && !"ADMIN".equals(request.role());
        if (demotingAdmin && target.isEnabled()) ensureAnotherActiveAdmin(id);
        UserAccountEntity actor = requireUser(actorId);
        String email = normalizeEmail(request.email());
        if (users.findByEmailIgnoreCase(email).filter(existing -> !existing.getId().equals(id)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
        String oldRole = target.getRole();
        target.updateProfile(email, request.displayName().trim());
        target.changeRole(request.role());
        try { users.flush(); }
        catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }
        String action = oldRole.equals(target.getRole()) ? "USER_UPDATED" : "USER_ROLE_CHANGED";
        auditService.record(actor, action, "USER", target.getId(), null,
                oldRole.equals(target.getRole()) ? "Profil modifié" : "Rôle modifié de " + oldRole + " vers " + target.getRole());
        return AdminUserResponse.from(target);
    }

    @Transactional
    public AdminUserResponse setEnabled(UUID actorId, UUID id, boolean enabled) {
        UserAccountEntity target = requireUser(id);
        if (!enabled && target.isEnabled() && "ADMIN".equals(target.getRole())) ensureAnotherActiveAdmin(id);
        UserAccountEntity actor = requireUser(actorId);
        target.setEnabled(enabled);
        auditService.record(actor, enabled ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", id, null,
                "Compte " + (enabled ? "activé" : "désactivé"));
        return AdminUserResponse.from(target);
    }

    @Transactional
    public void promoteByEmail(String suppliedEmail) {
        String email = normalizeEmail(suppliedEmail);
        UserAccountEntity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Aucun compte existant ne correspond à l’adresse fournie."));
        if (!user.isEnabled()) throw new IllegalStateException("Le compte doit être actif avant sa promotion.");
        if ("ADMIN".equals(user.getRole())) return;
        user.changeRole("ADMIN");
        auditService.recordCommand("ADMIN_PROMOTED_BY_COMMAND", "USER", user.getId(),
                "Promotion explicite via commande pour " + user.getEmail());
    }

    public AdminPageResponse<AdminAssociationResponse> listAssociations(String query, int page) {
        var result = associations.findAllByNameContainingIgnoreCase(query == null ? "" : query.trim(), page(page));
        return AdminPageResponse.from(result, association -> associationResponse(association));
    }

    public AdminAssociationResponse getAssociation(UUID id) { return associationResponse(requireAssociation(id)); }

    @Transactional
    public AdminAssociationResponse createAssociation(UUID actorId, AdminAssociationRequest request) {
        UserAccountEntity responsible = users.findByEmailIgnoreCase(normalizeEmail(request.responsibleEmail()))
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun compte actif ne correspond au responsable indiqué."));
        UserAccountEntity actor = requireUser(actorId);
        AssociationEntity association = associations.save(new AssociationEntity(request.name().trim(), clean(request.description()),
                clean(request.contactEmail()), clean(request.contactPhone()), clean(request.address())));
        associationMemberships.save(new AssociationMembershipEntity(association, responsible));
        auditService.record(actor, "ASSOCIATION_CREATED", "ASSOCIATION", association.getId(), association,
                "Association créée avec un responsable désigné");
        return associationResponse(association);
    }

    @Transactional
    public AdminAssociationResponse updateAssociation(UUID actorId, UUID id, AdminAssociationUpdateRequest request) {
        AssociationEntity association = requireAssociation(id);
        UserAccountEntity actor = requireUser(actorId);
        association.updateDetails(request.name().trim(), clean(request.description()), clean(request.contactEmail()),
                clean(request.contactPhone()), clean(request.address()));
        if (request.status() != null) association.setStatus(request.status());
        auditService.record(actor, request.status() == AssociationStatus.ARCHIVED
                        ? "ASSOCIATION_ARCHIVED" : "ASSOCIATION_UPDATED",
                "ASSOCIATION", id, association, "Informations d’association mises à jour");
        return associationResponse(association);
    }

    @Transactional
    public AdminAssociationResponse archiveAssociation(UUID actorId, UUID id) {
        AssociationEntity association = requireAssociation(id);
        UserAccountEntity actor = requireUser(actorId);
        association.archive();
        auditService.record(actor, "ASSOCIATION_ARCHIVED", "ASSOCIATION", id, association,
                "Association archivée ; données métier conservées");
        return associationResponse(association);
    }

    @Transactional
    public AssociationManagerResponse addAssociationResponsible(UUID actorId, UUID associationId,
            AddAssociationResponsibleRequest request) {
        AssociationEntity association = requireAssociation(associationId);
        UserAccountEntity responsible = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun compte actif ne correspond à cette adresse."));
        var membership = associationMemberships.findByAssociationIdAndUserAccountId(associationId, responsible.getId())
                .orElseGet(() -> new AssociationMembershipEntity(association, responsible));
        membership.activate();
        var saved = associationMemberships.save(membership);
        auditService.record(requireUser(actorId), "ASSOCIATION_RESPONSIBLE_ADDED", "ASSOCIATION_MEMBERSHIP",
                saved.getId(), association, "Responsable ajouté : " + responsible.getEmail());
        return new AssociationManagerResponse(saved.getId(), responsible.getId(), responsible.getEmail(),
                responsible.getDisplayName(), saved.isActive(), saved.getCreatedAt());
    }

    public AdminPageResponse<AdminAuditResponse> audit(int page) {
        return AdminPageResponse.from(audit.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), 25)), AdminAuditResponse::from);
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

    private UserAccountEntity requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Compte utilisateur introuvable."));
    }

    private void ensureAnotherActiveAdmin(UUID targetId) {
        var lockedAdmins = users.lockActiveAdministrators();
        if (lockedAdmins.size() <= 1 && lockedAdmins.stream().anyMatch(admin -> admin.getId().equals(targetId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le dernier administrateur actif ne peut pas être désactivé ni rétrogradé.");
        }
    }

    private void validateRole(String role, boolean allowAdmin) {
        if (!("RESPONSABLE".equals(role) || "TRESORIER".equals(role)
                || allowAdmin && "ADMIN".equals(role))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le rôle doit être RESPONSABLE ou TRESORIER. La promotion ADMIN utilise la commande dédiée.");
        }
    }

    private void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 6 || bytes > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Le mot de passe doit contenir au moins 6 caractères et au plus 72 octets UTF-8.");
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private PageRequest page(int page) { return PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "createdAt")); }
}
