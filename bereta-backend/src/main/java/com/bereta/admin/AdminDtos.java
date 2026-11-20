package com.bereta.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrats JSON de l'espace administrateur (comptes et journal). */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Toute action qui modifie des données exige un motif (10 caractères minimum). */
    public record ActionRequest(
            @NotBlank(message = "Le motif est obligatoire.")
            @Size(min = 10, max = 500, message = "Le motif doit contenir entre 10 et 500 caractères.") String reason) {
    }

    /** Ligne de la liste des comptes : coordonnées masquées. */
    public record UserSummary(UUID id, String displayName, String phone, String email, String role, boolean enabled,
                              boolean mustChangePassword, Instant createdAt) {
    }

    public record UserAssociation(UUID id, String name, String status) {
    }

    public record UserFund(UUID membershipId, String role, UUID associationId, String associationName,
                           String primaryFundName, UUID localFundId, String localFundName) {
    }

    /** Fiche détaillée : coordonnées complètes, rattachements, dernière connexion, sessions ouvertes. */
    public record UserDetail(UUID id, String displayName, String phone, String email, String role, boolean enabled,
                             boolean mustChangePassword, Instant createdAt, Instant lastLoginAt, long activeSessions,
                             List<UserAssociation> associations, List<UserFund> funds) {
    }

    public record UserActionResult(UserSummary user, long closedSessions) {
    }

    /** Mot de passe provisoire : renvoyé une seule fois, jamais journalisé ni stocké en clair. */
    public record PasswordResetResult(String temporaryPassword, long closedSessions) {
    }

    public record AuditEntry(UUID id, UUID actorId, String actorName, String action, String targetType, UUID targetId,
                             UUID associationId, String associationName, String reason, String details,
                             Map<String, Object> before, Map<String, Object> after, String ipAddress,
                             Instant createdAt) {
    }
}
