package com.bereta.imports.extraction;

import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Transforme un tableau (lignes de cellules) en membres, avec des en-têtes souples :
 * prénom / prenom / first name, nom / name, téléphone / tel / portable… Une colonne « nom complet »
 * est découpée (premier mot = prénom) et signalée à vérifier. Sans en-tête reconnu, l'ordre
 * prénom, nom, téléphone est supposé et signalé.
 */
public final class TabularMemberReader {

    public static final int MAX_ROWS = 500;
    private static final int HEADER_SEARCH_ROWS = 10;

    private static final Set<String> FIRST = Set.of("prenom", "prenoms", "firstname", "first", "givenname");
    private static final Set<String> LAST = Set.of("nom", "noms", "name", "lastname", "last", "surname",
            "nomdefamille", "familyname");
    private static final Set<String> FULL = Set.of("nomcomplet", "nometprenom", "nometprenoms", "nomprenom",
            "nomprenoms", "prenometnom", "prenomnom", "fullname", "membre", "membres", "nomsetprenoms");
    private static final Set<String> PHONE = Set.of("telephone", "tel", "portable", "mobile", "phone", "numero",
            "contact", "gsm", "cellulaire", "numerodetelephone", "telephoneportable", "phonenumber", "numtel",
            "notel", "telportable");

    private TabularMemberReader() {
    }

    public static List<ExtractedRow> read(List<List<String>> table) {
        int headerIndex = -1;
        Columns columns = null;
        for (int i = 0; i < Math.min(table.size(), HEADER_SEARCH_ROWS) && columns == null; i++) {
            columns = Columns.fromHeader(table.get(i));
            if (columns != null) headerIndex = i;
        }
        boolean positional = columns == null;
        if (positional) columns = new Columns(0, 1, -1, 2);

        List<ExtractedRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < table.size(); i++) {
            List<String> cells = table.get(i);
            if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
            if (rows.size() >= MAX_ROWS) {
                throw new ImportRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Le document contient plus de " + MAX_ROWS + " membres : découpez-le en plusieurs imports.");
            }
            rows.add(columns.read(cells, positional));
        }
        return rows;
    }

    /** « Téléphone portable » → « telephoneportable ». */
    static String key(String header) {
        if (header == null) return "";
        String plain = Normalizer.normalize(header, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record Columns(int first, int last, int full, int phone) {

        static Columns fromHeader(List<String> header) {
            int first = -1, last = -1, full = -1, phone = -1;
            for (int i = 0; i < header.size(); i++) {
                String key = key(header.get(i));
                if (first < 0 && FIRST.contains(key)) first = i;
                else if (last < 0 && LAST.contains(key)) last = i;
                else if (full < 0 && FULL.contains(key)) full = i;
                else if (phone < 0 && (PHONE.contains(key) || key.startsWith("telephone"))) phone = i;
            }
            boolean names = (first >= 0 && last >= 0) || full >= 0 || last >= 0;
            return names ? new Columns(first, last, full, phone) : null;
        }

        ExtractedRow read(List<String> cells, boolean positional) {
            String phoneValue = cell(cells, phone);
            if (first < 0 && (full >= 0 || last >= 0)) {
                // Une seule colonne de nom : découpage automatique, à faire vérifier.
                String whole = cell(cells, full >= 0 ? full : last);
                if (whole == null) return new ExtractedRow(null, null, phoneValue, null, null);
                String[] parts = whole.trim().split("\\s+", 2);
                return new ExtractedRow(parts[0], parts.length > 1 ? parts[1] : null, phoneValue, Confidence.MOYENNE,
                        "Prénom et nom séparés automatiquement");
            }
            if (positional) {
                return new ExtractedRow(cell(cells, first), cell(cells, last), phoneValue, Confidence.MOYENNE,
                        "Colonnes sans en-tête : ordre prénom, nom, téléphone supposé");
            }
            return new ExtractedRow(cell(cells, first), cell(cells, last), phoneValue, null, null);
        }

        private static String cell(List<String> cells, int index) {
            if (index < 0 || index >= cells.size()) return null;
            String value = cells.get(index);
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
