package com.bereta.auth;

/** Événements écrits dans auth_audit_log (colonne texte : les anciens événements restent lisibles). */
public enum AuthEvent {
    REGISTERED,
    LOGIN_FAILED,
    LOGIN_SUCCEEDED,
    ACCOUNT_LOCKED,
    REFRESH_REUSE_DETECTED,
    LOGOUT,
    LOGOUT_ALL_DEVICES,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    TEMPORARY_PASSWORD_ISSUED,
    TREASURER_ACCOUNT_CREATED,
    PROFILE_COMPLETED
}
