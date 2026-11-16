package com.bereta.imports;

import com.bereta.auth.RateLimiterService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.funds.FundAccessService;
import com.bereta.funds.FundScope;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.funds.PrimaryFundRepository;
import com.bereta.imports.ImportDtos.PreviewResponse;
import com.bereta.imports.extraction.ExtractedRow;
import com.bereta.imports.extraction.ImportRejectedException;
import com.bereta.imports.extraction.MemberExtractor;
import com.bereta.imports.extraction.TabularMemberReader;
import com.bereta.imports.extraction.UploadedFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Analyse d'un document : lecture des membres, puis création d'un import BROUILLON avec son aperçu.
 * Rien n'est créé dans les membres ; le document n'est jamais conservé (seule son empreinte l'est).
 */
@Service
public class MemberImportAnalysisService {

    private final FundAccessService fundAccess;
    private final LocalFundRepository localFunds;
    private final PrimaryFundRepository primaryFunds;
    private final UserAccountRepository users;
    private final MemberImportRepository imports;
    private final MemberImportRowRepository rows;
    private final List<MemberExtractor> extractors;
    private final ImportFileReader fileReader;
    private final ImportRowRules rules;
    private final ImportPreviewAssembler assembler;
    private final RateLimiterService rateLimiter;

    MemberImportAnalysisService(FundAccessService fundAccess, LocalFundRepository localFunds,
                                PrimaryFundRepository primaryFunds, UserAccountRepository users,
                                MemberImportRepository imports, MemberImportRowRepository rows,
                                List<MemberExtractor> extractors, ImportFileReader fileReader, ImportRowRules rules,
                                ImportPreviewAssembler assembler, RateLimiterService rateLimiter) {
        this.fundAccess = fundAccess;
        this.localFunds = localFunds;
        this.primaryFunds = primaryFunds;
        this.users = users;
        this.imports = imports;
        this.rows = rows;
        this.extractors = extractors;
        this.fileReader = fileReader;
        this.rules = rules;
        this.assembler = assembler;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public PreviewResponse analyse(UUID userId, UUID localFundId, List<MultipartFile> uploads) {
        FundScope scope = fundAccess.scope(userId);
        Destination destination = destination(scope, localFundId);
        List<UploadedFile> files = fileReader.read(uploads);
        rateLimiter.check(RateLimiterService.Limit.IMPORT_ANALYSE_PER_USER, userId.toString());

        MemberExtractor extractor = extractors.stream().filter(e -> e.supports(files.get(0).type())).findFirst()
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
                destination.primary(), destination.local(), creator, extractor.sourceType(),
                ImportFileReader.fingerprint(files)));
        List<MemberImportRowEntity> lines = new ArrayList<>();
        for (ExtractedRow read : extracted) {
            MemberImportRowEntity row = new MemberImportRowEntity(draft.getId(), lines.size() + 1);
            row.setRead(read.firstName(), read.lastName(), read.phone(), read.confidence(), read.remark());
            rules.applyValues(row, read.firstName(), read.lastName(), read.phone());
            lines.add(row);
        }
        rules.refresh(draft, lines);
        rows.saveAll(lines);
        draft.touch(lines.size());
        return assembler.preview(userId, draft, lines);
    }

    /** Responsable : caisse principale ou une caisse locale de l'association. Trésorier : ses caisses (403 sinon). */
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

    private record Destination(PrimaryFundEntity primary, LocalFundEntity local) {
    }
}
