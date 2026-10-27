package com.bereta.auth;

public record AuthUserResponse(String email, String displayName, String role) {
    static AuthUserResponse from(BeretaUserDetails user) {
        return new AuthUserResponse(user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
