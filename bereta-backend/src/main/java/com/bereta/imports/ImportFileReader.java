package com.bereta.imports;

import com.bereta.imports.extraction.FileTypeDetector;
import com.bereta.imports.extraction.ImportRejectedException;
import com.bereta.imports.extraction.UploadedFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Fichiers reçus : 1 document ou jusqu'à 5 photos, 10 Mo chacun, type vérifié par le contenu ; empreinte SHA-256. */
@Component
class ImportFileReader {

    static final int MAX_PHOTOS = 5;
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final FileTypeDetector detector;

    ImportFileReader(FileTypeDetector detector) {
        this.detector = detector;
    }

    List<UploadedFile> read(List<MultipartFile> uploads) {
        List<MultipartFile> present = uploads == null ? List.of()
                : uploads.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (present.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ajoutez une photo ou un fichier.");
        }
        if (present.size() > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MAX_PHOTOS + " photos maximum par import.");
        }
        List<UploadedFile> files = new ArrayList<>();
        for (MultipartFile upload : present) files.add(read(upload));
        if (files.size() > 1 && !files.stream().allMatch(file -> file.type().isImage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Plusieurs fichiers : uniquement des photos (une par page). Envoyez les autres documents un par un.");
        }
        return files;
    }

    private UploadedFile read(MultipartFile upload) {
        if (upload.getSize() > MAX_FILE_BYTES) {
            throw new ImportRejectedException(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (10 Mo maximum).");
        }
        try {
            byte[] content = upload.getBytes();
            return new UploadedFile(upload.getOriginalFilename(), content, detector.detect(content));
        } catch (IOException unreadable) {
            throw ImportRejectedException.unreadable("Impossible de lire le fichier envoyé.");
        }
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
}
