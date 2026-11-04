package com.bereta.auth;

import org.springframework.http.HttpStatus;

/**
 * Erreur d'authentification destinée à l'utilisateur (message en français).
 * Les services la déclarent en {@code noRollbackFor} quand un compteur
 * (échecs de connexion, essais d'un code) doit être conservé malgré l'erreur.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;

    public AuthException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public AuthException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Identifiants incorrects. Après 5 essais, l’accès est bloqué pendant 15 minutes.");
    }

    public static AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }
}
