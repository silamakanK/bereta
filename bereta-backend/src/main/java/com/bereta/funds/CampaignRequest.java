package com.bereta.funds;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CampaignRequest(
        @NotNull UUID primaryFundId,
        @NotBlank @Size(max = 120) String name,
        @NotNull CampaignType campaignType,
        Integer campaignYear,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal expectedAmount,
        LocalDate dueDate
) {
}
