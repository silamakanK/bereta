package com.bereta.admin;

import com.bereta.auth.UserAccountEntity;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String displayName, String role,
                                boolean enabled, Instant createdAt) {
    static AdminUserResponse from(UserAccountEntity user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
