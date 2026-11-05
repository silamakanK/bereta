package com.bereta.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code status} OUVERTE ou CLOTUREE ; {@code enrolledCount} = membres inscrits à la campagne. */
public record CampaignResponse(
        UUID id,
        UUID primaryFundId,
        String primaryFundName,
        String name,
        CampaignType campaignType,
        Integer campaignYear,
        BigDecimal expectedAmount,
        LocalDate dueDate,
        String status,
        Instant closedAt,
        long enrolledCount,
        Instant createdAt
) {
    static CampaignResponse from(CampaignEntity campaign, long enrolledCount) {
        return new CampaignResponse(campaign.getId(), campaign.getPrimaryFund().getId(),
                campaign.getPrimaryFund().getName(), campaign.getName(), campaign.getCampaignType(),
                campaign.getCampaignYear(), campaign.getExpectedAmount(), campaign.getDueDate(),
                campaign.getStatus(), campaign.getClosedAt(), enrolledCount, campaign.getCreatedAt());
    }
}
