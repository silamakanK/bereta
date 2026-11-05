package com.bereta.auth;

import java.util.UUID;

public record AuthUserResponse(UUID id, String displayName, String email, String phone, String role,
                               boolean mustChangePassword) {
    public static AuthUserResponse from(BeretaUserDetails user) {
        return new AuthUserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getPhoneE164(),
                user.getRole(), user.isMustChangePassword());
    }

    public static AuthUserResponse from(UserAccountEntity user) {
        return from(new BeretaUserDetails(user));
    }
}
