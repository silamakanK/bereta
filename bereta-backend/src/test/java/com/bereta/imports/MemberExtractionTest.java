package com.bereta.imports;

import com.bereta.auth.PhoneNumberService;
import com.bereta.imports.extraction.Confidence;
import com.bereta.imports.extraction.CsvMemberExtractor;
import com.bereta.imports.extraction.DetectedFileType;
import com.bereta.imports.extraction.ExcelMemberExtractor;
import com.bereta.imports.extraction.ExtractedRow;
import com.bereta.imports.extraction.FileTypeDetector;
import com.bereta.imports.extraction.ImportRejectedException;
import com.bereta.imports.extraction.UploadedFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lecture des fichiers structurés (sans IA), détection du type par le contenu, normalisation. */
class MemberExtractionTest {

    private final FileTypeDetector detector = new FileTypeDetector();
    private final ImportNormalizer normalizer = new ImportNormalizer(new PhoneNumberService("FR"));

    @Test
    void readsSemicolonCsvInUtf8WithFlexibleHeaders() throws IOException {
        List<ExtractedRow> rows = csv("membres-point-virgule-utf8.csv");
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0)).isEqualTo(new ExtractedRow("fatou", "KAMISSOKO", "06 12 34 56 78", null, null));
        assertThat(rows.get(2).firstName()).isEqualTo("Aïssata");
        assertThat(rows.get(4).firstName()).isNull();
        assertThat(rows.get(4).lastName()).isEqualTo("Keïta");
    }

    @Test
    void readsCommaCsvInWindows1252WithQuotedSeparators() throws IOException {
        byte[] content = resource("membres-virgule-windows1252.csv");
        assertThat(new String(content, StandardCharsets.UTF_8)).contains("�"); // pas de l'UTF-8
        List<ExtractedRow> rows = new CsvMemberExtractor().extract(List.of(file(content)));
        assertThat(rows).containsExactly(
                new ExtractedRow("Hélène", "Bâ", "07 98 76 54 32", null, null),
                new ExtractedRow("Jean-François", "Le Gall, aîné", null, null, null));
    }

    @Test
    void splitsAFullNameColumnAndFlagsIt() throws IOException {
        List<ExtractedRow> rows = csv("membres-nom-complet.csv");
        assertThat(rows.get(0)).isEqualTo(new ExtractedRow("Awa", "Diallo", "+223 76 11 22 33",
                Confidence.MOYENNE, "Prénom et nom séparés automatiquement"));
        assertThat(rows.get(1).firstName()).isEqualTo("Sidi");
        assertThat(rows.get(1).lastName()).isNull();
    }

    @Test
    void assumesColumnOrderWithoutHeaderAndFlagsIt() {
        byte[] content = "Moussa;Diarra;0612345678\nBinta;Sangaré;\n".getBytes(StandardCharsets.UTF_8);
        List<ExtractedRow> rows = new CsvMemberExtractor().extract(List.of(file(content)));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).lastName()).isEqualTo("Diarra");
        assertThat(rows.get(0).confidence()).isEqualTo(Confidence.MOYENNE);
    }

    @Test
    void readsTheFirstExcelSheetAfterATitle() throws IOException {
        byte[] content = resource("membres.xlsx");
        assertThat(detector.detect(content)).isEqualTo(DetectedFileType.XLSX);
        List<ExtractedRow> rows = new ExcelMemberExtractor().extract(
                List.of(new UploadedFile("membres.xlsx", content, DetectedFileType.XLSX)));
        assertThat(rows).containsExactly(
                new ExtractedRow("Binta", "Sangaré", "0698765432", null, null),
                new ExtractedRow("Moussa", "Diarra", "612340000", null, null));
        assertThat(normalizer.normalizePhone("612340000").e164()).isEqualTo("+33612340000");
    }

    @Test
    void refusesMoreThan500Members() {
        StringBuilder csv = new StringBuilder("prenom;nom\n");
        for (int i = 0; i < 501; i++) csv.append("P").append(i).append(";N").append(i).append('\n');
        assertThatThrownBy(() -> new CsvMemberExtractor().extract(
                List.of(file(csv.toString().getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(ImportRejectedException.class).hasMessageContaining("500");
    }

    @Test
    void detectsTheTypeFromTheContentNotTheName() {
        assertThat(detector.detect("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))).isEqualTo(DetectedFileType.PDF);
        assertThat(detector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10})).isEqualTo(DetectedFileType.JPEG);
        assertThat(detector.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10})).isEqualTo(DetectedFileType.PNG);
        assertThat(detector.detect(new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0})).isEqualTo(DetectedFileType.HEIC);
        assertThatThrownBy(() -> detector.detect(new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1}))
                .hasMessageContaining(".xlsx");
        assertThatThrownBy(() -> detector.detect(new byte[]{'P', 'K', 3, 4, 0, 0})).hasMessageContaining("Format non pris en charge");
        assertThatThrownBy(() -> detector.detect(new byte[]{1, 2, 0, 4})).hasMessageContaining("Format non pris en charge");
    }

    @Test
    void normalizesNamesAndPhones() {
        assertThat(normalizer.name("  fatou   KAMISSOKO-diallo ")).isEqualTo("Fatou Kamissoko-Diallo");
        assertThat(normalizer.name("n'diaye")).isEqualTo("N'Diaye");
        assertThat(normalizer.name("   ")).isNull();
        assertThat(normalizer.normalizePhone("06 12 34 56 78")).isEqualTo(new ImportNormalizer.PhoneResult("+33612345678", false, false));
        assertThat(normalizer.normalizePhone("+221 77 123 45 67").e164()).isEqualTo("+221771234567");
        assertThat(normalizer.normalizePhone("00223 76 12 34 56").e164()).isEqualTo("+22376123456");
        // Sans indicatif, invalide en France : indicatif malien déduit, signalé à vérifier.
        assertThat(normalizer.normalizePhone("76 12 34 56")).isEqualTo(new ImportNormalizer.PhoneResult("+22376123456", true, false));
        assertThat(normalizer.normalizePhone("12345").invalid()).isTrue();
        assertThat(normalizer.normalizePhone(" ").invalid()).isFalse();
        assertThat(ImportNormalizer.nameKey("Aïssata", "TRAORÉ")).isEqualTo(ImportNormalizer.nameKey("aissata", "Traore"));
        assertThat(DuplicateDetector.atMostOneEdit("moussa|diarra", "mousa|diarra")).isTrue();
        assertThat(DuplicateDetector.atMostOneEdit("moussa|diarra", "moussa|diallo")).isFalse();
    }

    private List<ExtractedRow> csv(String name) throws IOException {
        byte[] content = resource(name);
        assertThat(detector.detect(content)).isEqualTo(DetectedFileType.CSV);
        return new CsvMemberExtractor().extract(List.of(file(content)));
    }

    private static UploadedFile file(byte[] content) {
        return new UploadedFile("membres.csv", content, DetectedFileType.CSV);
    }

    static byte[] resource(String name) throws IOException {
        try (InputStream in = MemberExtractionTest.class.getResourceAsStream("/imports/" + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }
}
