package com.bereta.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Size(min = 6, max = 72) String password
) {
}
