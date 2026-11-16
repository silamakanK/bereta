package com.bereta.imports;

import com.bereta.imports.extraction.Confidence;
import com.bereta.members.MemberRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Règles d'une ligne d'aperçu : valeurs normalisées, doublons, statut et problèmes à afficher. */
@Component
class ImportRowRules {

    private final ImportNormalizer normalizer;
    private final MemberRepository members;

    ImportRowRules(ImportNormalizer normalizer, MemberRepository members) {
        this.normalizer = normalizer;
        this.members = members;
    }

    void applyValues(MemberImportRowEntity row, String firstName, String lastName, String phone) {
        row.setValues(truncate(normalizer.name(firstName)), truncate(normalizer.name(lastName)),
                normalizer.phone(phone), normalizer.normalizePhone(phone).e164());
    }

    /** Doublons recalculés avec les membres actuels de l'association, puis statut de chaque ligne. */
    void refresh(MemberImportEntity draft, List<MemberImportRowEntity> lines) {
        new DuplicateDetector(members.findAllByAssociationIdOrderByLastNameAscFirstNameAsc(
                draft.getAssociation().getId())).apply(lines);
        for (MemberImportRowEntity row : lines) {
            if (!row.isDuplicate()) row.setDuplicateAction(null);
            else if (row.getDuplicateAction() == null) row.setDuplicateAction(DuplicateAction.IGNORER);
            row.setStatus(evaluate(row).status());
        }
    }

    /**
     * Problème bloquant (prénom, nom, téléphone impossible) → À vérifier ; sinon doublon ; sinon un avertissement
     * non confirmé par l'utilisateur → À vérifier ; sinon Prête.
     */
    Evaluation evaluate(MemberImportRowEntity row) {
        ImportNormalizer.PhoneResult phone = normalizer.normalizePhone(row.getPhone());
        List<String> blocking = blockingIssues(row, phone);
        List<String> warnings = warnings(row, phone);
        boolean pendingWarnings = !warnings.isEmpty() && !row.isConfirmed();
        ImportRowStatus status;
        if (!blocking.isEmpty()) status = ImportRowStatus.A_VERIFIER;
        else if (row.isDuplicate()) status = ImportRowStatus.DOUBLON;
        else if (pendingWarnings) status = ImportRowStatus.A_VERIFIER;
        else status = ImportRowStatus.PRETE;
        List<String> issues = new ArrayList<>(blocking);
        if (!row.isConfirmed()) issues.addAll(warnings);
        return new Evaluation(status, issues, blocking.isEmpty());
    }

    private static List<String> blockingIssues(MemberImportRowEntity row, ImportNormalizer.PhoneResult phone) {
        List<String> blocking = new ArrayList<>();
        if (row.getFirstName() == null) blocking.add("Prénom manquant");
        if (row.getLastName() == null) blocking.add("Nom manquant");
        if (phone.invalid()) blocking.add("Téléphone invalide");
        return blocking;
    }

    private static List<String> warnings(MemberImportRowEntity row, ImportNormalizer.PhoneResult phone) {
        List<String> warnings = new ArrayList<>();
        String phoneWarning = phoneWarning(row, phone);
        if (phoneWarning != null) warnings.add(phoneWarning);
        if (row.getConfidence() == Confidence.BASSE) warnings.add("Lecture incertaine");
        else if (row.getConfidence() == Confidence.MOYENNE) {
            warnings.add(row.getRemark() != null ? row.getRemark() : "Lecture à vérifier");
        }
        return warnings;
    }

    private static String phoneWarning(MemberImportRowEntity row, ImportNormalizer.PhoneResult phone) {
        if (phone.invalid()) return null;
        if (row.getPhone() == null) return "Téléphone manquant";
        if (phone.deduced()) return "Indicatif déduit (" + phone.e164().substring(0, 4) + ") : vérifiez";
        if (phone.unusual()) return "Numéro inhabituel : vérifiez-le";
        return null;
    }

    static boolean willBeCreated(MemberImportRowEntity row) {
        return row.isIncluded() && (row.getStatus() == ImportRowStatus.PRETE
                || (row.getStatus() == ImportRowStatus.DOUBLON && row.getDuplicateAction() == DuplicateAction.CREER));
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 100 ? value : value.substring(0, 100);
    }

    /** {@code confirmable} : aucun problème bloquant, « Ligne vérifiée » suffit à valider la ligne. */
    record Evaluation(ImportRowStatus status, List<String> issues, boolean confirmable) {
    }
}
