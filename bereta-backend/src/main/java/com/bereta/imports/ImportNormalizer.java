package com.bereta.imports;

import com.bereta.auth.PhoneNumberService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Nettoyage des valeurs lues : noms (espaces, majuscule initiale) et téléphones (E.164). */
@Component
public class ImportNormalizer {

    /** Régions essayées, après la région par défaut, pour un numéro écrit sans indicatif. */
    static final List<String> FALLBACK_REGIONS = List.of("ML", "SN");

    private final PhoneNumberService phones;

    public ImportNormalizer(PhoneNumberService phones) {
        this.phones = phones;
    }

    /** «  fatou   KAMISSOKO-diallo » → « Fatou Kamissoko-Diallo » ; « n'diaye » → « N'Diaye ». */
    public String name(String raw) {
        if (raw == null) return null;
        String collapsed = raw.strip().replaceAll("\\s+", " ");
        if (collapsed.isEmpty()) return null;
        StringBuilder out = new StringBuilder(collapsed.length());
        boolean startOfWord = true;
        for (int i = 0; i < collapsed.length(); ) {
            int cp = collapsed.codePointAt(i);
            out.appendCodePoint(startOfWord ? Character.toTitleCase(cp) : Character.toLowerCase(cp));
            startOfWord = cp == ' ' || cp == '-' || cp == '\'' || cp == '’';
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /** Téléphone tel que saisi, nettoyé des espaces superflus (vide → null). */
    public String phone(String raw) {
        if (raw == null) return null;
        String cleaned = raw.strip().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }

    public PhoneResult normalizePhone(String raw) {
        String phone = phone(raw);
        if (phone == null) return PhoneResult.EMPTY;
        Optional<String> normalized = phones.tryNormalize(phone);
        if (normalized.isPresent()) return new PhoneResult(normalized.get(), false, false);
        if (!phone.startsWith("+") && !phone.startsWith("00")) {
            for (String region : FALLBACK_REGIONS) {
                if (region.equalsIgnoreCase(phones.defaultRegion())) continue;
                Optional<String> other = phones.tryNormalize(phone, region);
                if (other.isPresent()) return new PhoneResult(other.get(), true, false);
            }
        }
        return PhoneResult.INVALID;
    }

    /** Clé de comparaison des noms : sans accents, sans casse, sans ponctuation. */
    public static String nameKey(String firstName, String lastName) {
        return plain(firstName) + "|" + plain(lastName);
    }

    static String plain(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /**
     * @param e164        numéro normalisé, ou null
     * @param deduced     indicatif déduit (numéro sans indicatif, non valide pour la région par défaut)
     * @param invalid     numéro présent mais illisible
     */
    public record PhoneResult(String e164, boolean deduced, boolean invalid) {
        static final PhoneResult EMPTY = new PhoneResult(null, false, false);
        static final PhoneResult INVALID = new PhoneResult(null, false, true);
    }
}
