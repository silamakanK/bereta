package com.bereta.funds;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MemberCampaignResponse(
        UUID id,
        UUID campaignId,
        String campaignName,
        CampaignType campaignType,
        Integer campaignYear,
        String primaryFundName,
        UUID localFundId,
        String localFundName,
        BigDecimal expectedAmount,
        Instant createdAt
) {
    static MemberCampaignResponse from(MemberCampaignEntity enrollment) {
        CampaignEntity campaign = enrollment.getCampaign();
        LocalFundEntity localFund = enrollment.getLocalFund();
        return new MemberCampaignResponse(enrollment.getId(), campaign.getId(), campaign.getName(),
                campaign.getCampaignType(), campaign.getCampaignYear(), campaign.getPrimaryFund().getName(),
                localFund.getId(), localFund.getName(), enrollment.getExpectedAmount(), enrollment.getCreatedAt());
    }
}
