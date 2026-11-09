package com.bereta.imports.extraction;

/** Fichier reçu, gardé en mémoire le temps de l'analyse seulement (jamais écrit sur disque ni en base). */
public record UploadedFile(String name, byte[] content, DetectedFileType type) {
}
