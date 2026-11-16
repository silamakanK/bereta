package com.bereta.imports;

import com.bereta.funds.CampaignEntity;
import com.bereta.funds.CampaignRepository;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.imports.ImportDtos.Counters;
import com.bereta.imports.ImportDtos.ExistingMember;
import com.bereta.imports.ImportDtos.FundRef;
import com.bereta.imports.ImportDtos.PreviewResponse;
import com.bereta.imports.ImportDtos.RowResponse;
import com.bereta.members.MemberEntity;
import com.bereta.members.MemberRepository;
import com.bereta.members.MemberService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Construit l'aperçu renvoyé au navigateur : lignes, doublons (nom masqué hors périmètre), compteurs. */
@Component
class ImportPreviewAssembler {

    static final String PRIMARY_FUND_LABEL = "Caisse principale";

    private final MemberImportRepository imports;
    private final MemberRepository members;
    private final MemberService memberService;
    private final CampaignRepository campaigns;
    private final ImportRowRules rules;

    ImportPreviewAssembler(MemberImportRepository imports, MemberRepository members, MemberService memberService,
                           CampaignRepository campaigns, ImportRowRules rules) {
        this.imports = imports;
        this.members = members;
        this.memberService = memberService;
        this.campaigns = campaigns;
        this.rules = rules;
    }

    PreviewResponse preview(UUID userId, MemberImportEntity draft, List<MemberImportRowEntity> lines) {
        Set<UUID> visibleMembers = memberService.visibleMembers(userId).stream().map(MemberEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, MemberEntity> duplicates = new HashMap<>();
        members.findAllById(lines.stream().map(MemberImportRowEntity::getDuplicateMemberId)
                        .filter(Objects::nonNull).distinct().toList())
                .forEach(member -> duplicates.put(member.getId(), member));
        List<RowResponse> responses = lines.stream()
                .map(row -> row(row, duplicates.get(row.getDuplicateMemberId()), visibleMembers)).toList();
        Instant alreadyImportedAt = imports.findFirstByAssociationIdAndFileFingerprintAndStatusOrderByValidatedAtDesc(
                        draft.getAssociation().getId(), draft.getFileFingerprint(), ImportStatus.VALIDE)
                .filter(previous -> !previous.getId().equals(draft.getId()))
                .map(MemberImportEntity::getValidatedAt).orElse(null);
        return new PreviewResponse(draft.getId(), draft.getStatus(), draft.getSourceType(), fund(draft),
                draft.getCreatedAt(), alreadyImportedAt,
                currentCampaign(draft.getPrimaryFund()).map(CampaignEntity::getName).orElse(null),
                counters(lines), responses);
    }

    private RowResponse row(MemberImportRowEntity row, MemberEntity existing, Set<UUID> visibleMembers) {
        ExistingMember duplicate = existingMember(existing, visibleMembers);
        ImportRowRules.Evaluation evaluation = rules.evaluate(row);
        List<String> issues = new ArrayList<>(evaluation.issues());
        String duplicateIssue = duplicateIssue(row, duplicate);
        if (duplicateIssue != null) issues.add(duplicateIssue);
        return new RowResponse(row.getId(), row.getLineNumber(), row.getReadFirstName(), row.getReadLastName(),
                row.getReadPhone(), row.getFirstName(), row.getLastName(), row.getPhone(), row.getPhoneE164(),
                row.getConfidence(), row.getRemark(), row.getStatus(), issues, evaluation.confirmable(),
                row.isIncluded(), row.isConfirmed(), duplicate, row.getDuplicateOfLine(), row.getDuplicateAction());
    }

    /** Membre existant : détaillé s'il est visible par l'utilisateur, anonyme sinon. */
    private static ExistingMember existingMember(MemberEntity existing, Set<UUID> visibleMembers) {
        if (existing == null) return null;
        if (!visibleMembers.contains(existing.getId())) return new ExistingMember(null, null, null, null, false);
        String fundName = existing.getCreatedInLocalFund() != null
                ? existing.getCreatedInLocalFund().getName() : PRIMARY_FUND_LABEL;
        return new ExistingMember(existing.getId(), existing.getFirstName(), existing.getLastName(), fundName, true);
    }

    private static String duplicateIssue(MemberImportRowEntity row, ExistingMember duplicate) {
        if (duplicate != null) {
            return duplicate.visible() ? "Déjà membre : " + duplicate.firstName() + " " + duplicate.lastName()
                    : "Déjà membre de l’association (autre caisse)";
        }
        return row.getDuplicateOfLine() == null ? null : "Même personne que la ligne " + row.getDuplicateOfLine();
    }

    private static Counters counters(List<MemberImportRowEntity> lines) {
        int excluded = (int) lines.stream().filter(r -> !r.isIncluded()).count();
        int toCreate = (int) lines.stream().filter(ImportRowRules::willBeCreated).count();
        return new Counters(lines.size(), count(lines, ImportRowStatus.PRETE), count(lines, ImportRowStatus.A_VERIFIER),
                count(lines, ImportRowStatus.DOUBLON), excluded, toCreate, lines.size() - toCreate);
    }

    private static int count(List<MemberImportRowEntity> lines, ImportRowStatus status) {
        return (int) lines.stream().filter(r -> r.isIncluded() && r.getStatus() == status).count();
    }

    Optional<CampaignEntity> currentCampaign(PrimaryFundEntity primary) {
        return campaigns.findAllByPrimaryFundIdOrderByCreatedAtDesc(primary.getId()).stream()
                .filter(CampaignEntity::isOpen).findFirst();
    }

    static FundRef fund(MemberImportEntity found) {
        LocalFundEntity local = found.getLocalFund();
        if (local == null) return new FundRef(found.getPrimaryFund().getId(), null, PRIMARY_FUND_LABEL, true);
        return new FundRef(found.getPrimaryFund().getId(), local.getId(), local.getName(), false);
    }
}
