package com.bereta.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserCreateRequest(@NotBlank @Email @Size(max=320) String email,
                                     @NotBlank @Size(max=120) String displayName,
                                     @NotBlank @Size(min=12, max=72) String password,
                                     @NotBlank String role) { }
