package com.bereta.imports.extraction;

import java.util.List;

/** Lit les membres (prénom, nom, téléphone) d'un ou plusieurs fichiers du même type. */
public interface MemberExtractor {

    boolean supports(DetectedFileType type);

    ImportSourceType sourceType();

    List<ExtractedRow> extract(List<UploadedFile> files);
}
