package com.bereta.imports;

import com.bereta.auth.RateLimiterService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.CampaignEntity;
import com.bereta.funds.CampaignRepository;
import com.bereta.funds.FundAccessService;
import com.bereta.funds.FundScope;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.MemberCampaignEntity;
import com.bereta.funds.MemberCampaignRepository;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.imports.ImportDtos.Counters;
import com.bereta.imports.ImportDtos.ExistingMember;
import com.bereta.imports.ImportDtos.FundRef;
import com.bereta.imports.ImportDtos.PreviewResponse;
import com.bereta.imports.ImportDtos.RowResponse;
import com.bereta.imports.ImportDtos.RowUpdate;
import com.bereta.imports.ImportDtos.RowsUpdateRequest;
import com.bereta.imports.ImportDtos.SummaryResponse;
import com.bereta.imports.ImportDtos.ValidateRequest;
import com.bereta.imports.ImportDtos.ValidationResponse;
import com.bereta.imports.extraction.Confidence;
import com.bereta.imports.extraction.DetectedFileType;
import com.bereta.imports.extraction.ExtractedRow;
import com.bereta.imports.extraction.FileTypeDetector;
import com.bereta.imports.extraction.ImportRejectedException;
import com.bereta.imports.extraction.MemberExtractor;
import com.bereta.imports.extraction.TabularMemberReader;
import com.bereta.imports.extraction.UploadedFile;
import com.bereta.members.MemberEntity;
import com.bereta.members.MemberRepository;
import com.bereta.members.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import de membres : analyse d'un document → brouillon modifiable → validation en une transaction.
 * Rien n'est créé avant la validation ; le document n'est jamais conservé (seule son empreinte l'est).
 */
@Service
@Transactional(readOnly = true)
public class MemberImportService {

    static final int MAX_PHOTOS = 5;
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final String PRIMARY_FUND_LABEL = "Caisse principale";

    private final MemberImportRepository imports;
    private final MemberImportRowRepository rows;
    private final MemberRepository members;
    private final MemberService memberService;
    private final FundAccessService fundAccess;
    private final LocalFundRepository localFunds;
    private final PrimaryFundRepository primaryFunds;
    private final UserAccountRepository users;
    private final CampaignRepository campaigns;
    private final MemberCampaignRepository enrollments;
    private final FileTypeDetector detector;
    private final List<MemberExtractor> extractors;
    private final ImportNormalizer normalizer;
    private final RateLimiterService rateLimiter;

    public MemberImportService(MemberImportRepository imports, MemberImportRowRepository rows,
                               MemberRepository members, MemberService memberService, FundAccessService fundAccess,
                               LocalFundRepository localFunds, PrimaryFundRepository primaryFunds,
                               UserAccountRepository users, CampaignRepository campaigns,
                               MemberCampaignRepository enrollments, FileTypeDetector detector,
                               List<MemberExtractor> extractors, ImportNormalizer normalizer,
                               RateLimiterService rateLimiter) {
        this.imports = imports;
        this.rows = rows;
        this.members = members;
        this.memberService = memberService;
        this.fundAccess = fundAccess;
        this.localFunds = localFunds;
        this.primaryFunds = primaryFunds;
        this.users = users;
        this.campaigns = campaigns;
        this.enrollments = enrollments;
        this.detector = detector;
        this.extractors = extractors;
        this.normalizer = normalizer;
        this.rateLimiter = rateLimiter;
    }

    // ---- Analyse -----------------------------------------------------------------------------

    @Transactional
    public PreviewResponse analyse(UUID userId, UUID localFundId, List<MultipartFile> uploads) {
        FundScope scope = fundAccess.scope(userId);
        Destination destination = destination(scope, localFundId);
        List<UploadedFile> files = read(uploads);
        rateLimiter.check(RateLimiterService.Limit.IMPORT_ANALYSE_PER_USER, userId.toString());

        DetectedFileType type = files.get(0).type();
        MemberExtractor extractor = extractors.stream().filter(e -> e.supports(type)).findFirst()
                .orElseThrow(() -> new ImportRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "La lecture de ce type de document n’est pas encore disponible. Utilisez un fichier Excel ou CSV."));
        List<ExtractedRow> extracted = extractor.extract(files);
        if (extracted.isEmpty()) {
            throw ImportRejectedException.unreadable("Aucun membre trouvé dans ce document.");
        }
        if (extracted.size() > TabularMemberReader.MAX_ROWS) {
            throw ImportRejectedException.unreadable("Le document contient plus de " + TabularMemberReader.MAX_ROWS
                    + " membres : découpez-le en plusieurs imports.");
        }

        UserAccountEntity creator = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        MemberImportEntity draft = imports.save(new MemberImportEntity(destination.primary().getAssociation(),
                destination.primary(), destination.local(), creator, extractor.sourceType(), fingerprint(files)));

        List<MemberImportRowEntity> lines = new ArrayList<>();
        int lineNumber = 1;
        for (ExtractedRow read : extracted) {
            MemberImportRowEntity row = new MemberImportRowEntity(draft.getId(), lineNumber++);
            row.setRead(read.firstName(), read.lastName(), read.phone(), read.confidence(), read.remark());
            applyValues(row, read.firstName(), read.lastName(), read.phone());
            lines.add(row);
        }
        refresh(draft, lines);
        rows.saveAll(lines);
        draft.touch(lines.size());
        return preview(userId, draft, lines);
    }

    private Destination destination(FundScope scope, UUID localFundId) {
        if (localFundId == null) {
            if (!scope.manager()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Un trésorier importe uniquement dans sa caisse locale.");
            }
            PrimaryFundEntity primary = primaryFunds.findActiveByAssociationId(scope.associationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "L’association n’a pas encore de caisse principale."));
            return new Destination(primary, null);
        }
        LocalFundEntity local = localFunds.findById(localFundId)
                .filter(found -> found.getAssociation().getId().equals(scope.associationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable."));
        if (!scope.covers(local)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n’avez pas accès à cette caisse locale.");
        }
        return new Destination(local.getPrimaryFund(), local);
    }

    private List<UploadedFile> read(List<MultipartFile> uploads) {
        List<MultipartFile> present = uploads == null ? List.of()
                : uploads.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (present.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez une photo ou un fichier.");
        }
        if (present.size() > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MAX_PHOTOS + " photos maximum par import.");
        }
        List<UploadedFile> files = new ArrayList<>();
        for (MultipartFile upload : present) {
            if (upload.getSize() > MAX_FILE_BYTES) {
                throw new ImportRejectedException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (10 Mo maximum).");
            }
            try {
                byte[] content = upload.getBytes();
                files.add(new UploadedFile(upload.getOriginalFilename(), content, detector.detect(content)));
            } catch (IOException unreadable) {
                throw ImportRejectedException.unreadable("Impossible de lire le fichier envoyé.");
            }
        }
        boolean images = files.get(0).type().isImage();
        if (files.size() > 1 && !files.stream().allMatch(file -> file.type().isImage() && images)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plusieurs fichiers : uniquement des photos (une par page). Envoyez les autres documents un par un.");
        }
        return files;
    }

    /** SHA-256 du contenu (des pages, dans l'ordre, pour plusieurs photos). */
    static String fingerprint(List<UploadedFile> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (UploadedFile file : files) digest.update(file.content());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Corrections ------------------------------------------------------------------------

    public PreviewResponse get(UUID userId, UUID importId) {
        MemberImportEntity draft = load(userId, importId, false);
        return preview(userId, draft, rows.findAllByImportIdOrderByLineNumberAsc(importId));
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
            MemberImportRowEntity row;
            if (change.id() == null) {
                row = new MemberImportRowEntity(importId, nextLine++);
            } else {
                row = current.remove(change.id());
                if (row == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ligne inconnue dans cet import.");
            }
            applyValues(row, change.firstName(), change.lastName(), change.phone());
            row.setIncluded(change.included());
            row.setConfirmed(change.confirmed());
            row.setDuplicateAction(change.duplicateAction());
            updated.add(row);
        }
        rows.deleteAll(current.values());
        updated.sort(Comparator.comparingInt(MemberImportRowEntity::getLineNumber));
        refresh(draft, updated);
        rows.saveAll(updated);
        draft.touch(updated.size());
        imports.save(draft);
        return preview(userId, draft, updated);
    }

    // ---- Validation (une seule fois) ---------------------------------------------------------

    @Transactional
    public ValidationResponse validate(UUID userId, UUID importId, ValidateRequest request) {
        // Verrou de ligne : un double clic attend la fin de la première validation, puis voit VALIDE → 409.
        MemberImportEntity draft = load(userId, importId, true);
        requireDraft(draft);
        List<MemberImportRowEntity> lines = rows.findAllByImportIdOrderByLineNumberAsc(importId);
        refresh(draft, lines);

        long toCheck = lines.stream().filter(r -> r.isIncluded() && r.getStatus() == ImportRowStatus.A_VERIFIER).count();
        if (toCheck > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, toCheck == 1
                    ? "1 ligne est à vérifier : corrigez-la ou décochez-la."
                    : toCheck + " lignes sont à vérifier : corrigez-les ou décochez-les.");
        }
        List<MemberImportRowEntity> creatable = lines.stream().filter(MemberImportService::willBeCreated).toList();
        if (creatable.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun membre à créer : toutes les lignes sont exclues ou ignorées.");
        }
        CampaignEntity campaign = request != null && request.enrollInCurrentCampaign()
                ? currentCampaign(draft.getPrimaryFund()).orElseThrow(() -> new ResponseStatusException(
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
                draft.getEnrolledCount(), campaign == null ? null : campaign.getName(), fund(draft));
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
                .map(found -> new SummaryResponse(found.getId(), found.getStatus(), found.getSourceType(), fund(found),
                        found.getCreatedBy() == null ? null : found.getCreatedBy().getDisplayName(),
                        found.getRowCount(), found.getCreatedCount(), found.getIgnoredCount(),
                        found.getEnrolledCount(), found.getCreatedAt(), found.getValidatedAt()))
                .toList();
    }

    // ---- Règles des lignes -------------------------------------------------------------------

    private void applyValues(MemberImportRowEntity row, String firstName, String lastName, String phone) {
        row.setValues(truncate(normalizer.name(firstName)), truncate(normalizer.name(lastName)),
                normalizer.phone(phone), normalizer.normalizePhone(phone).e164());
    }

    /** Doublons recalculés avec les membres actuels de l'association, puis statut de chaque ligne. */
    private void refresh(MemberImportEntity draft, List<MemberImportRowEntity> lines) {
        new DuplicateDetector(members.findAllByAssociationIdOrderByLastNameAscFirstNameAsc(
                draft.getAssociation().getId())).apply(lines);
        for (MemberImportRowEntity row : lines) {
            if (!row.isDuplicate()) row.setDuplicateAction(null);
            else if (row.getDuplicateAction() == null) row.setDuplicateAction(DuplicateAction.IGNORER);
            row.setStatus(evaluate(row).status());
        }
    }

    private Evaluation evaluate(MemberImportRowEntity row) {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (row.getFirstName() == null) blocking.add("Prénom manquant");
        if (row.getLastName() == null) blocking.add("Nom manquant");
        ImportNormalizer.PhoneResult phone = normalizer.normalizePhone(row.getPhone());
        if (phone.invalid()) blocking.add("Téléphone invalide");
        else if (row.getPhone() == null) warnings.add("Téléphone manquant");
        else if (phone.deduced()) warnings.add("Indicatif déduit (" + phone.e164().substring(0, 4) + ") : vérifiez");
        if (row.getConfidence() == Confidence.BASSE) warnings.add("Lecture incertaine");
        else if (row.getConfidence() == Confidence.MOYENNE) {
            warnings.add(row.getRemark() != null ? row.getRemark() : "Lecture à vérifier");
        }
        List<String> issues = new ArrayList<>(blocking);
        ImportRowStatus status;
        if (!blocking.isEmpty()) status = ImportRowStatus.A_VERIFIER;
        else if (row.isDuplicate()) status = ImportRowStatus.DOUBLON;
        else if (!warnings.isEmpty() && !row.isConfirmed()) status = ImportRowStatus.A_VERIFIER;
        else status = ImportRowStatus.PRETE;
        if (!row.isConfirmed()) issues.addAll(warnings);
        return new Evaluation(status, issues);
    }

    static boolean willBeCreated(MemberImportRowEntity row) {
        return row.isIncluded() && (row.getStatus() == ImportRowStatus.PRETE
                || (row.getStatus() == ImportRowStatus.DOUBLON && row.getDuplicateAction() == DuplicateAction.CREER));
    }

    // ---- Accès et réponses -------------------------------------------------------------------

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

    private Optional<CampaignEntity> currentCampaign(PrimaryFundEntity primary) {
        return campaigns.findAllByPrimaryFundIdOrderByCreatedAtDesc(primary.getId()).stream()
                .filter(CampaignEntity::isOpen).findFirst();
    }

    private PreviewResponse preview(UUID userId, MemberImportEntity draft, List<MemberImportRowEntity> lines) {
        Set<UUID> visibleMembers = memberService.visibleMembers(userId).stream().map(MemberEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, MemberEntity> duplicates = new HashMap<>();
        members.findAllById(lines.stream().map(MemberImportRowEntity::getDuplicateMemberId)
                        .filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(member -> duplicates.put(member.getId(), member));

        List<RowResponse> responses = lines.stream().map(row -> {
            MemberEntity existing = row.getDuplicateMemberId() == null ? null : duplicates.get(row.getDuplicateMemberId());
            ExistingMember duplicate = existing == null ? null : visibleMembers.contains(existing.getId())
                    ? new ExistingMember(existing.getId(), existing.getFirstName(), existing.getLastName(),
                    memberFund(existing), true)
                    : new ExistingMember(null, null, null, null, false);
            Evaluation evaluation = evaluate(row);
            List<String> issues = new ArrayList<>(evaluation.issues());
            if (existing != null) {
                issues.add(duplicate.visible() ? "Déjà membre : " + existing.getFirstName() + " " + existing.getLastName()
                        : "Déjà membre de l’association (autre caisse)");
            } else if (row.getDuplicateOfLine() != null) {
                issues.add("Même personne que la ligne " + row.getDuplicateOfLine());
            }
            return new RowResponse(row.getId(), row.getLineNumber(), row.getReadFirstName(), row.getReadLastName(),
                    row.getReadPhone(), row.getFirstName(), row.getLastName(), row.getPhone(), row.getPhoneE164(),
                    row.getConfidence(), row.getRemark(), row.getStatus(), issues, row.isIncluded(), row.isConfirmed(),
                    duplicate, row.getDuplicateOfLine(), row.getDuplicateAction());
        }).toList();

        int excluded = (int) lines.stream().filter(r -> !r.isIncluded()).count();
        int ready = count(lines, ImportRowStatus.PRETE);
        int toCheck = count(lines, ImportRowStatus.A_VERIFIER);
        int duplicatesCount = count(lines, ImportRowStatus.DOUBLON);
        int toCreate = (int) lines.stream().filter(MemberImportService::willBeCreated).count();
        Instant alreadyImportedAt = imports.findFirstByAssociationIdAndFileFingerprintAndStatusOrderByValidatedAtDesc(
                        draft.getAssociation().getId(), draft.getFileFingerprint(), ImportStatus.VALIDE)
                .filter(previous -> !previous.getId().equals(draft.getId()))
                .map(MemberImportEntity::getValidatedAt).orElse(null);
        return new PreviewResponse(draft.getId(), draft.getStatus(), draft.getSourceType(), fund(draft),
                draft.getCreatedAt(), alreadyImportedAt,
                currentCampaign(draft.getPrimaryFund()).map(CampaignEntity::getName).orElse(null),
                new Counters(lines.size(), ready, toCheck, duplicatesCount, excluded, toCreate, lines.size() - toCreate),
                responses);
    }

    private static int count(List<MemberImportRowEntity> lines, ImportRowStatus status) {
        return (int) lines.stream().filter(r -> r.isIncluded() && r.getStatus() == status).count();
    }

    private static FundRef fund(MemberImportEntity found) {
        LocalFundEntity local = found.getLocalFund();
        return new FundRef(found.getPrimaryFund().getId(), local == null ? null : local.getId(),
                local == null ? PRIMARY_FUND_LABEL : local.getName(), local == null);
    }

    private static String memberFund(MemberEntity member) {
        return member.getCreatedInLocalFund() != null ? member.getCreatedInLocalFund().getName() : PRIMARY_FUND_LABEL;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 100 ? value : value.substring(0, 100);
    }

    private record Destination(PrimaryFundEntity primary, LocalFundEntity local) {
    }

    private record Evaluation(ImportRowStatus status, List<String> issues) {
    }
}
