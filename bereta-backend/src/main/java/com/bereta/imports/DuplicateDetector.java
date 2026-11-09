package com.bereta.imports;

import com.bereta.members.MemberEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doublons possibles, dans cet ordre : membre existant de l'association avec le même téléphone (E.164),
 * puis même nom et prénom (sans accents ni casse, ordre inversé accepté, une faute de frappe tolérée),
 * puis ligne précédente du même document (même téléphone ou même nom).
 */
final class DuplicateDetector {

    private static final int FUZZY_MIN_LENGTH = 8;

    private final Map<String, MemberEntity> byPhone = new HashMap<>();
    private final Map<String, MemberEntity> byName = new HashMap<>();

    DuplicateDetector(List<MemberEntity> existing) {
        for (MemberEntity member : existing) {
            if (member.getPhoneE164() != null) byPhone.putIfAbsent(member.getPhoneE164(), member);
            byName.putIfAbsent(ImportNormalizer.nameKey(member.getFirstName(), member.getLastName()), member);
        }
    }

    /** Recalcule les doublons des lignes incluses (les lignes exclues n'en ont pas). */
    void apply(List<MemberImportRowEntity> rows) {
        Map<String, Integer> seenPhones = new HashMap<>();
        Map<String, Integer> seenNames = new HashMap<>();
        for (MemberImportRowEntity row : rows) {
            row.setDuplicate(null, null);
            if (!row.isIncluded()) continue;
            String key = ImportNormalizer.nameKey(row.getFirstName(), row.getLastName());
            boolean named = row.getFirstName() != null && row.getLastName() != null;
            MemberEntity existing = findExisting(row.getPhoneE164(), named ? key : null,
                    ImportNormalizer.nameKey(row.getLastName(), row.getFirstName()));
            if (existing != null) {
                row.setDuplicate(existing.getId(), null);
            } else if (row.getPhoneE164() != null && seenPhones.containsKey(row.getPhoneE164())) {
                row.setDuplicate(null, seenPhones.get(row.getPhoneE164()));
            } else if (named && seenNames.containsKey(key)) {
                row.setDuplicate(null, seenNames.get(key));
            }
            if (row.getPhoneE164() != null) seenPhones.putIfAbsent(row.getPhoneE164(), row.getLineNumber());
            if (named) seenNames.putIfAbsent(key, row.getLineNumber());
        }
    }

    private MemberEntity findExisting(String phoneE164, String key, String swappedKey) {
        if (phoneE164 != null && byPhone.containsKey(phoneE164)) return byPhone.get(phoneE164);
        if (key == null) return null;
        if (byName.containsKey(key)) return byName.get(key);
        if (byName.containsKey(swappedKey)) return byName.get(swappedKey);
        if (key.length() < FUZZY_MIN_LENGTH) return null;
        for (Map.Entry<String, MemberEntity> candidate : byName.entrySet()) {
            if (Math.abs(candidate.getKey().length() - key.length()) <= 1 && atMostOneEdit(candidate.getKey(), key)) {
                return candidate.getValue();
            }
        }
        return null;
    }

    /** Distance d'édition ≤ 1 (une lettre ajoutée, retirée ou remplacée). */
    static boolean atMostOneEdit(String a, String b) {
        if (a.equals(b)) return true;
        if (a.length() > b.length()) return atMostOneEdit(b, a);
        int i = 0;
        while (i < a.length() && a.charAt(i) == b.charAt(i)) i++;
        if (a.length() == b.length()) return a.substring(i + 1).equals(b.substring(i + 1));
        return a.substring(i).equals(b.substring(i + 1));
    }

}
