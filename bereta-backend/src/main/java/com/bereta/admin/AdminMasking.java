package com.bereta.admin;

/** Masquage partiel des coordonnées dans les listes : « 06 •• •• •• 78 », « si•••@gmail.com ». */
final class AdminMasking {

    private static final char DOT = '•';

    private AdminMasking() {
    }

    /** Numéro déjà mis en forme (national ou international) : indicatif, 2 premiers et 2 derniers chiffres conservés. */
    static String phone(String formatted) {
        if (formatted == null || formatted.isBlank()) return null;
        String prefix = "";
        String rest = formatted;
        if (formatted.startsWith("+") && formatted.contains(" ")) {
            prefix = formatted.substring(0, formatted.indexOf(' ') + 1);
            rest = formatted.substring(prefix.length());
        }
        long digits = rest.chars().filter(Character::isDigit).count();
        StringBuilder masked = new StringBuilder(prefix);
        int seen = 0;
        for (char c : rest.toCharArray()) {
            if (!Character.isDigit(c)) {
                masked.append(c);
                continue;
            }
            masked.append(seen < 2 || seen >= digits - 2 ? c : DOT);
            seen++;
        }
        return masked.toString();
    }

    static String email(String email) {
        if (email == null || email.isBlank()) return null;
        int at = email.indexOf('@');
        if (at <= 0) return DOT + "" + DOT + DOT;
        String visible = email.substring(0, Math.min(2, at));
        return visible + String.valueOf(DOT).repeat(3) + email.substring(at);
    }
}
