package com.bereta.imports.extraction;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** CSV : encodage (UTF-8, sinon Windows-1252) et séparateur ({@code ;}, {@code ,} ou tabulation) détectés. */
@Component
public class CsvMemberExtractor implements MemberExtractor {

    static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Override
    public boolean supports(DetectedFileType type) {
        return type == DetectedFileType.CSV;
    }

    @Override
    public ImportSourceType sourceType() {
        return ImportSourceType.CSV;
    }

    @Override
    public List<ExtractedRow> extract(List<UploadedFile> files) {
        List<ExtractedRow> rows = new ArrayList<>();
        for (UploadedFile file : files) rows.addAll(TabularMemberReader.read(parse(decode(file.content()))));
        return rows;
    }

    static String decode(byte[] content) {
        int offset = content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, offset, content.length - offset, WINDOWS_1252);
        }
    }

    static char detectDelimiter(String text) {
        String firstLine = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        char best = ';';
        long bestCount = 0;
        for (char candidate : new char[]{';', ',', '\t'}) {
            long count = countOutsideQuotes(firstLine, candidate);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    private static long countOutsideQuotes(String line, char delimiter) {
        long count = 0;
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') quoted = !quoted;
            else if (c == delimiter && !quoted) count++;
        }
        return count;
    }

    static List<List<String>> parse(String text) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(detectDelimiter(text))
                .setIgnoreEmptyLines(true).setTrim(true).setIgnoreSurroundingSpaces(true).get();
        List<List<String>> table = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                table.add(record.toList());
                if (table.size() > TabularMemberReader.MAX_ROWS + 20) break; // la limite est contrôlée ensuite
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalStateException | IllegalArgumentException malformed) {
            throw ImportRejectedException.unreadable("Fichier CSV mal formé : " + malformed.getMessage());
        }
        return table;
    }
}
