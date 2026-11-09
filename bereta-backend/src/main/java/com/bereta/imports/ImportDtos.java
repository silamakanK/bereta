package com.bereta.imports;

import com.bereta.imports.extraction.Confidence;
import com.bereta.imports.extraction.ImportSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats JSON de l'API d'import. */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** Caisse de destination : {@code localFundId} vide = caisse principale. */
    public record FundRef(UUID primaryFundId, UUID localFundId, String name, boolean primary) {
    }

    /** Membre existant proposé comme doublon ; le nom n'est donné que si l'utilisateur voit ce membre. */
    public record ExistingMember(UUID id, String firstName, String lastName, String fundName, boolean visible) {
    }

    public record RowResponse(
            UUID id,
            int lineNumber,
            String readFirstName,
            String readLastName,
            String readPhone,
            String firstName,
            String lastName,
            String phone,
            String phoneE164,
            Confidence confidence,
            String remark,
            ImportRowStatus status,
            List<String> issues,
            boolean included,
            boolean confirmed,
            ExistingMember duplicateMember,
            Integer duplicateOfLine,
            DuplicateAction duplicateAction
    ) {
    }

    /** Compteurs de l'aperçu : {@code toCreate} = lignes qui seront créées si l'on valide maintenant. */
    public record Counters(int total, int ready, int toCheck, int duplicates, int excluded, int toCreate, int ignored) {
    }

    public record PreviewResponse(
            UUID id,
            ImportStatus status,
            ImportSourceType sourceType,
            FundRef fund,
            Instant createdAt,
            Instant alreadyImportedAt,
            String currentCampaignName,
            Counters counters,
            List<RowResponse> rows
    ) {
    }

    public record RowUpdate(
            UUID id,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 40) String phone,
            boolean included,
            boolean confirmed,
            DuplicateAction duplicateAction
    ) {
    }

    /** Liste complète des lignes : une ligne sans id est ajoutée, une ligne absente est supprimée. */
    public record RowsUpdateRequest(@NotNull @Size(max = 500) List<@Valid RowUpdate> rows) {
    }

    public record ValidateRequest(boolean enrollInCurrentCampaign) {
    }

    public record ValidationResponse(UUID id, int created, int ignored, int enrolled, String campaignName,
                                     FundRef fund) {
    }

    public record SummaryResponse(UUID id, ImportStatus status, ImportSourceType sourceType, FundRef fund,
                                  String createdBy, int rowCount, int createdCount, int ignoredCount,
                                  int enrolledCount, Instant createdAt, Instant validatedAt) {
    }
}
