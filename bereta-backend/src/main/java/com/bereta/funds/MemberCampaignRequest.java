package com.bereta.funds;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MemberCampaignRequest(@NotNull UUID campaignId, @NotNull UUID localFundId) {
}
