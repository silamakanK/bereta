package com.bereta.funds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRemittanceRequest(@NotBlank @Size(max = 500) String reason) {
}
