package com.bereta.imports.extraction;

/** Ligne brute lue dans un document, avant normalisation. Une valeur illisible reste vide (jamais inventée). */
public record ExtractedRow(String firstName, String lastName, String phone, Confidence confidence, String remark) {
}
