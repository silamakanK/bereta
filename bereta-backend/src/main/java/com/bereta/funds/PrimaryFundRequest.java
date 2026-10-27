package com.bereta.funds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrimaryFundRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 180) String objective,
        @Size(max = 1000) String description
) {
}
