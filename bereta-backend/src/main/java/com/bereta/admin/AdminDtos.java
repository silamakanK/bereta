package com.bereta.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrats JSON de l'espace administrateur (comptes, associations, caisses et journal). */
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
    // ---- Associations et caisses (lot 2) ----------------------------------------------------

    /** Transfert : fromUserId (responsable actuel) perd ses droits, toUserId (compte actif) les reçoit. */
    public record TransferRequest(
            @NotNull(message = "Choisissez le responsable à remplacer.") UUID fromUserId,
            @NotNull(message = "Choisissez le nouveau responsable.") UUID toUserId,
            @NotBlank(message = "Le motif est obligatoire.")
            @Size(min = 10, max = 500, message = "Le motif doit contenir entre 10 et 500 caractères.") String reason) {
    }

    /** Réaffectation : replaceUserId (facultatif) est remplacé par toUserId ; sans lui, toUserId est ajouté. */
    public record ReassignTreasurerRequest(
            @NotNull(message = "Choisissez le nouveau trésorier.") UUID toUserId,
            UUID replaceUserId,
            @NotBlank(message = "Le motif est obligatoire.")
            @Size(min = 10, max = 500, message = "Le motif doit contenir entre 10 et 500 caractères.") String reason) {
    }

    /** Personne rattachée (fiche détaillée : coordonnées complètes). */
    public record Person(UUID userId, String displayName, String phone, String email, String role, boolean enabled) {
    }

    /** Argent d'une caisse locale : encaissé, encore sur place, remis en attente de confirmation. */
    public record Balance(BigDecimal collected, BigDecimal availableLocally, BigDecimal inTransit) {
        static final Balance EMPTY = new Balance(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record AssociationSummary(UUID id, String name, String status, Instant createdAt, String responsibleName,
                                     long localFundCount, long memberCount) {
    }

    public record FundLine(UUID id, String name, String location, String status, List<Person> treasurers,
                           long memberCount, Balance balance) {
    }

    /** Soldes de l'association : total encaissé, solde central (direct + remises confirmées), en transit, sur place. */
    public record AssociationBalances(BigDecimal totalCollected, BigDecimal central, BigDecimal inTransit,
                                      BigDecimal availableLocally) {
    }

    public record AssociationDetail(UUID id, String name, String description, String contactEmail, String contactPhone,
                                    String address, String status, Instant createdAt, List<Person> responsibles,
                                    UUID primaryFundId, String primaryFundName, List<FundLine> localFunds,
                                    long memberCount, long activeMemberCount, long campaignCount,
                                    long openCampaignCount, AssociationBalances balances) {
    }

    public record FundSummary(UUID id, String name, String location, String status, UUID associationId,
                              String associationName, String associationStatus, List<String> treasurerNames,
                              Balance balance, Instant createdAt) {
    }

    public record FundDetail(UUID id, String name, String location, String description, String status,
                             UUID associationId, String associationName, String associationStatus,
                             String primaryFundName, Instant createdAt, List<Person> treasurers, long memberCount,
                             Balance balance) {
    }
}
