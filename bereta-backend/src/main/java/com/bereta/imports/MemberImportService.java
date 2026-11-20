package com.bereta.imports;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.CampaignEntity;
import com.bereta.funds.FundAccessService;
import com.bereta.funds.FundScope;
import com.bereta.funds.MemberCampaignEntity;
import com.bereta.funds.MemberCampaignRepository;
import com.bereta.imports.ImportDtos.PreviewResponse;
import com.bereta.imports.ImportDtos.RowUpdate;
import com.bereta.imports.ImportDtos.RowsUpdateRequest;
import com.bereta.imports.ImportDtos.SummaryResponse;
import com.bereta.imports.ImportDtos.ValidateRequest;
import com.bereta.imports.ImportDtos.ValidationResponse;
import com.bereta.members.MemberEntity;
import com.bereta.members.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Brouillon d'import : corrections, validation en une seule transaction (une seule fois), annulation, historique.
 * L'analyse du document est dans {@link MemberImportAnalysisService}.
 */
@Service
@Transactional(readOnly = true)
public class MemberImportService {

    private final MemberImportRepository imports;
    private final MemberImportRowRepository rows;
    private final MemberRepository members;
    private final FundAccessService fundAccess;
    private final UserAccountRepository users;
    private final MemberCampaignRepository enrollments;
    private final ImportRowRules rules;
    private final ImportPreviewAssembler assembler;

    MemberImportService(MemberImportRepository imports, MemberImportRowRepository rows, MemberRepository members,
                        FundAccessService fundAccess, UserAccountRepository users,
                        MemberCampaignRepository enrollments, ImportRowRules rules, ImportPreviewAssembler assembler) {
        this.imports = imports;
        this.rows = rows;
        this.members = members;
        this.fundAccess = fundAccess;
        this.users = users;
        this.enrollments = enrollments;
        this.rules = rules;
        this.assembler = assembler;
    }

    public PreviewResponse get(UUID userId, UUID importId) {
        MemberImportEntity draft = load(userId, importId, false);
        return assembler.preview(userId, draft, rows.findAllByImportIdOrderByLineNumberAsc(importId));
    }

    @Transactional
    public PreviewResponse updateRows(UUID userId, UUID importId, RowsUpdateRequest request) {
        MemberImportEntity draft = load(userId, importId, false);
        requireDraft(draft);
        Map<UUID, MemberImportRowEntity> current = rows.findAllByImportIdOrderByLineNumberAsc(importId).stream()
                .collect(Collectors.toMap(MemberImportRowEntity::getId, Function.identity()));
        int nextLine = current.values().stream().mapToInt(MemberImportRowEntity::getLineNumber).max().orElse(0) + 1;
        List<MemberImportRowEntity> updated = new ArrayList<>();
        for (RowUpdate change : request.rows()) {
            MemberImportRowEntity row = change.id() == null
                    ? new MemberImportRowEntity(importId, nextLine++) : current.remove(change.id());
            if (row == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ligne inconnue dans cet import.");
            rules.applyValues(row, change.firstName(), change.lastName(), change.phone());
            row.setIncluded(change.included());
            row.setConfirmed(change.confirmed());
            row.setDuplicateAction(change.duplicateAction());
            updated.add(row);
        }
        rows.deleteAll(current.values());
        updated.sort(Comparator.comparingInt(MemberImportRowEntity::getLineNumber));
        rules.refresh(draft, updated);
        rows.saveAll(updated);
        draft.touch(updated.size());
        imports.save(draft);
        return assembler.preview(userId, draft, updated);
    }

    @Transactional
    public ValidationResponse validate(UUID userId, UUID importId, ValidateRequest request) {
        // Verrou de ligne : un double clic attend la fin de la première validation, puis voit VALIDE → 409.
        MemberImportEntity draft = load(userId, importId, true);
        requireDraft(draft);
        FundAccessService.requireOpen(draft.getLocalFund());
        List<MemberImportRowEntity> lines = rows.findAllByImportIdOrderByLineNumberAsc(importId);
        rules.refresh(draft, lines);
        List<MemberImportRowEntity> creatable = creatableRows(lines);
        CampaignEntity campaign = request != null && request.enrollInCurrentCampaign()
                ? assembler.currentCampaign(draft.getPrimaryFund()).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Aucune campagne ouverte : décochez l’inscription à la campagne."))
                : null;

        UserAccountEntity creator = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        List<MemberEntity> created = new ArrayList<>();
        for (MemberImportRowEntity row : creatable) {
            MemberEntity member = new MemberEntity(draft.getAssociation(), row.getFirstName(), row.getLastName(),
                    row.getPhone(), creator, draft.getPrimaryFund(), draft.getLocalFund());
            member.setPhoneE164(row.getPhoneE164());
            member.setImportId(draft.getId());
            created.add(member);
        }
        members.saveAll(created);
        // Case cochée : inscription manuelle par le responsable de la caisse (même caisse que le membre).
        if (campaign != null) {
            for (MemberEntity member : created) {
                enrollments.save(new MemberCampaignEntity(member, campaign, draft.getLocalFund(),
                        campaign.getExpectedAmount()));
            }
        }
        draft.validate(created.size(), lines.size() - created.size(), campaign == null ? 0 : created.size());
        rows.deleteAll(lines); // RGPD : l'aperçu ne sert plus, les membres sont créés
        imports.save(draft);
        return new ValidationResponse(draft.getId(), draft.getCreatedCount(), draft.getIgnoredCount(),
                draft.getEnrolledCount(), campaign == null ? null : campaign.getName(), ImportPreviewAssembler.fund(draft));
    }

    /** Lignes à créer ; refus s'il reste des lignes à vérifier ou s'il n'y a rien à créer. */
    private static List<MemberImportRowEntity> creatableRows(List<MemberImportRowEntity> lines) {
        long toCheck = lines.stream().filter(r -> r.isIncluded() && r.getStatus() == ImportRowStatus.A_VERIFIER).count();
        if (toCheck > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, toCheck == 1
                    ? "1 ligne est à vérifier : corrigez-la ou décochez-la."
                    : toCheck + " lignes sont à vérifier : corrigez-les ou décochez-les.");
        }
        List<MemberImportRowEntity> creatable = lines.stream().filter(ImportRowRules::willBeCreated).toList();
        if (creatable.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun membre à créer : toutes les lignes sont exclues ou ignorées.");
        }
        return creatable;
    }

    @Transactional
    public void cancel(UUID userId, UUID importId) {
        MemberImportEntity draft = load(userId, importId, true);
        requireDraft(draft);
        rows.deleteAll(rows.findAllByImportIdOrderByLineNumberAsc(importId));
        draft.cancel();
        imports.save(draft);
    }

    public List<SummaryResponse> history(UUID userId) {
        FundScope scope = fundAccess.scope(userId);
        return imports.findAllByAssociationIdOrderByCreatedAtDesc(scope.associationId()).stream()
                .filter(found -> visible(scope, found))
                .map(found -> new SummaryResponse(found.getId(), found.getStatus(), found.getSourceType(),
                        ImportPreviewAssembler.fund(found),
                        found.getCreatedBy() == null ? null : found.getCreatedBy().getDisplayName(),
                        found.getRowCount(), found.getCreatedCount(), found.getIgnoredCount(),
                        found.getEnrolledCount(), found.getCreatedAt(), found.getValidatedAt()))
                .toList();
    }

    /** Import de l'association active, dans le périmètre de l'utilisateur (404 sinon). */
    private MemberImportEntity load(UUID userId, UUID importId, boolean forUpdate) {
        FundScope scope = fundAccess.scope(userId);
        Optional<MemberImportEntity> found = forUpdate ? imports.findByIdForUpdate(importId) : imports.findById(importId);
        return found.filter(candidate -> candidate.getAssociation().getId().equals(scope.associationId()))
                .filter(candidate -> visible(scope, candidate))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import introuvable."));
    }

    private static boolean visible(FundScope scope, MemberImportEntity found) {
        return scope.manager() || (found.getLocalFund() != null && scope.covers(found.getLocalFund()));
    }

    private static void requireDraft(MemberImportEntity found) {
        if (found.getStatus() == ImportStatus.VALIDE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet import a déjà été validé.");
        }
        if (found.getStatus() == ImportStatus.ANNULE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet import a été annulé.");
        }
    }
}
