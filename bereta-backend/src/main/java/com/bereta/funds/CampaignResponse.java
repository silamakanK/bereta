package com.bereta.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        UUID primaryFundId,
        String primaryFundName,
        String name,
        CampaignType campaignType,
        Integer campaignYear,
        BigDecimal expectedAmount,
        LocalDate dueDate,
        Instant createdAt
) {
    static CampaignResponse from(CampaignEntity campaign) {
        return new CampaignResponse(campaign.getId(), campaign.getPrimaryFund().getId(),
                campaign.getPrimaryFund().getName(), campaign.getName(), campaign.getCampaignType(),
                campaign.getCampaignYear(), campaign.getExpectedAmount(), campaign.getDueDate(), campaign.getCreatedAt());
    }
}
