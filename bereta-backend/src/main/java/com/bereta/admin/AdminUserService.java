package com.bereta.admin;

import com.bereta.admin.AdminDtos.PasswordResetResult;
import com.bereta.admin.AdminDtos.UserActionResult;
import com.bereta.admin.AdminDtos.UserDetail;
import com.bereta.admin.AdminDtos.UserSummary;
import com.bereta.auth.PasswordService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.auth.token.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Comptes vus par l'administrateur : recherche, fiche, désactivation (sessions fermées immédiatement),
 * réactivation et mot de passe provisoire. Chaque action est journalisée dans sa propre transaction ;
 * une action refusée ne laisse aucune entrée.
 */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserAccountRepository users;
    private final AdminUserReader reader;
    private final RefreshTokenService refreshTokens;
    private final PasswordService passwords;
    private final AdminAuditService audit;

    AdminUserService(UserAccountRepository users, AdminUserReader reader, RefreshTokenService refreshTokens,
                     PasswordService passwords, AdminAuditService audit) {
        this.users = users;
        this.reader = reader;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.audit = audit;
    }

    public AdminPageResponse<UserSummary> list(AdminUserQuery.Filter filter, int page, int size) {
        return reader.list(filter, page, size);
    }

    public UserDetail detail(UUID id) {
        return reader.detail(id);
    }

    /** Connexion refusée, jeton d'accès invalidé (version), refresh tokens révoqués. Données conservées. */
    @Transactional
    public UserActionResult disable(UUID actorId, UUID id, String reason) {
        UserAccountEntity actor = require(actorId);
        if (actorId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous ne pouvez pas désactiver votre propre compte.");
        }
        UserAccountEntity target = require(id);
        if (!target.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce compte est déjà désactivé.");
        if ("ADMIN".equals(target.getRole())) ensureAnotherActiveAdmin(id);
        long sessions = activeSessions(id);
        target.setEnabled(false);
        target.bumpTokenVersion();
        refreshTokens.revokeAll(id, "ADMIN_DISABLED");
        audit.record(actor, new AdminAuditService.Entry(AdminAction.USER_DISABLED, "USER", id, null, reason,
                Map.of("enabled", true), Map.of("enabled", false, "closedSessions", sessions)));
        return new UserActionResult(summary(target), sessions);
    }

    @Transactional
    public UserActionResult enable(UUID actorId, UUID id, String reason) {
        UserAccountEntity actor = require(actorId);
        UserAccountEntity target = require(id);
        if (target.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce compte est déjà actif.");
        target.setEnabled(true);
        audit.record(actor, new AdminAuditService.Entry(AdminAction.USER_ENABLED, "USER", id, null, reason,
                Map.of("enabled", false), Map.of("enabled", true)));
        return new UserActionResult(summary(target), 0);
    }

    /** Récupération d'accès sans e-mail : mot de passe provisoire affiché une seule fois, changement obligatoire. */
    @Transactional
    public PasswordResetResult resetPassword(UUID actorId, UUID id, String reason) {
        UserAccountEntity actor = require(actorId);
        if (actorId.equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Changez votre propre mot de passe depuis votre compte, pas depuis l’administration.");
        }
        require(id);
        long sessions = activeSessions(id);
        String temporaryPassword = passwords.resetByAdministrator(actorId, id);
        audit.record(actor, new AdminAuditService.Entry(AdminAction.USER_PASSWORD_RESET, "USER", id, null, reason,
                Map.of("mustChangePassword", false), Map.of("mustChangePassword", true, "closedSessions", sessions)));
        return new PasswordResetResult(temporaryPassword, sessions);
    }

    private UserSummary summary(UserAccountEntity user) {
        return reader.summary(user);
    }

    private long activeSessions(UUID userId) {
        return reader.activeSessions(userId);
    }

    private UserAccountEntity require(UUID id) {
        return reader.require(id);
    }

    /** Verrou sur les administrateurs actifs : deux désactivations simultanées ne peuvent pas retirer le dernier. */
    private void ensureAnotherActiveAdmin(UUID targetId) {
        var activeAdmins = users.lockActiveAdministrators();
        if (activeAdmins.stream().noneMatch(admin -> !admin.getId().equals(targetId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le dernier administrateur actif ne peut pas être désactivé.");
        }
    }
}
