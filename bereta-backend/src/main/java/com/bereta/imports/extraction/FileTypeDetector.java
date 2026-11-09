package com.bereta.imports.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Détermine le type réel d'un fichier à partir de ses premiers octets (signature), jamais de son extension. */
@Component
public class FileTypeDetector {

    private static final int MAX_ZIP_ENTRIES = 200;

    public DetectedFileType detect(byte[] content) {
        if (content == null || content.length == 0) {
            throw ImportRejectedException.unreadable("Le fichier est vide.");
        }
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return DetectedFileType.PDF;
        if (startsWith(content, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) return DetectedFileType.PNG;
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return DetectedFileType.JPEG;
        if (content.length > 12 && startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && ascii(content, 8, 4).equals("WEBP")) return DetectedFileType.WEBP;
        if (content.length > 12 && ascii(content, 4, 4).equals("ftyp")) {
            String brand = ascii(content, 8, 4);
            if (brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1")) {
                return DetectedFileType.HEIC;
            }
        }
        if (startsWith(content, new byte[]{'P', 'K', 3, 4})) {
            if (isXlsx(content)) return DetectedFileType.XLSX;
            throw unsupported();
        }
        if (startsWith(content, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})) {
            throw new ImportRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Ancien format Excel (.xls) : enregistrez le fichier au format .xlsx, puis réessayez.");
        }
        if (looksLikeText(content)) return DetectedFileType.CSV;
        throw unsupported();
    }

    private static ImportRejectedException unsupported() {
        return new ImportRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Format non pris en charge. Envoyez une photo (JPEG, PNG, HEIC), un PDF, un fichier Excel (.xlsx) ou CSV.");
    }

    private static boolean isXlsx(byte[] content) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null && count++ < MAX_ZIP_ENTRIES) {
                if (entry.getName().equals("xl/workbook.xml")) return true;
            }
            return false;
        } catch (IOException | IllegalArgumentException corrupted) {
            return false;
        }
    }

    /** Texte (CSV) : pas d'octet nul ni de caractère de contrôle binaire dans les premiers Ko. */
    private static boolean looksLikeText(byte[] content) {
        int limit = Math.min(content.length, 8192);
        for (int i = 0; i < limit; i++) {
            int b = content[i] & 0xFF;
            if (b == 0 || (b < 0x09) || (b > 0x0D && b < 0x20 && b != 0x1A)) return false;
        }
        return true;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) return false;
        }
        return true;
    }

    private static String ascii(byte[] content, int offset, int length) {
        return new String(content, offset, length, StandardCharsets.US_ASCII);
    }
}
