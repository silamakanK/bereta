package com.bereta.imports.extraction;

/** Type réel d'un fichier, déterminé par son contenu (et non par son extension). */
public enum DetectedFileType {
    CSV(false), XLSX(false), PDF(false), JPEG(true), PNG(true), WEBP(true), HEIC(true);

    private final boolean image;

    DetectedFileType(boolean image) {
        this.image = image;
    }

    public boolean isImage() { return image; }
}
