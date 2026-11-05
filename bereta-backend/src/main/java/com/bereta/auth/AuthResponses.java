package com.bereta.auth;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, AuthUserResponse user) {
    }

    public record MessageResponse(String message) {
    }

    /** Réinitialisation par un administrateur : mot de passe provisoire montré une seule fois. */
    public record TemporaryPasswordResponse(String temporaryPassword) {
    }
}
