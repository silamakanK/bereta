package com.bereta.funds;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignTreasurerRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull UUID localFundId
) {
}
