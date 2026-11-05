package com.bereta.funds;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code localFundId} absent : l'inscription est suivie par la caisse du membre (locale, ou principale). */
public record MemberCampaignRequest(@NotNull UUID campaignId, UUID localFundId) {
}
