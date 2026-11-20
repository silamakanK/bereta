package com.bereta.admin;

/**
 * Actions journalisées par l'espace administrateur. La colonne reste textuelle : les entrées plus anciennes
 * (avant ce lot) gardent leur libellé d'origine et restent lisibles.
 */
public enum AdminAction {
    USER_DISABLED,
    USER_ENABLED,
    USER_PASSWORD_RESET,
    ASSOCIATION_ARCHIVED
}
