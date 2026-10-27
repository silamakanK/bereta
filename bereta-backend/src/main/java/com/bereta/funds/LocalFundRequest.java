package com.bereta.funds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalFundRequest(@NotBlank @Size(max = 100) String name) {
}
