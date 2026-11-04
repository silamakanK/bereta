package com.bereta.auth;

import java.nio.charset.StandardCharsets;

/** Règle de la maquette : au moins 8 caractères dont 1 chiffre (BCrypt limite à 72 octets). */
public final class PasswordPolicy {

    public static final String RULE = "Le mot de passe doit contenir au moins 8 caractères, dont 1 chiffre.";

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.length() < 8 || password.chars().noneMatch(Character::isDigit)) {
            throw AuthException.badRequest("PASSWORD_POLICY", RULE);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AuthException.badRequest("PASSWORD_POLICY", "Le mot de passe ne peut pas dépasser 72 octets.");
        }
    }
}
