CREATE TABLE member_campaigns (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES members (id),
    campaign_id UUID NOT NULL REFERENCES campaigns (id),
    local_fund_id UUID NOT NULL REFERENCES local_funds (id),
    expected_amount NUMERIC(12, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_member_campaign UNIQUE (member_id, campaign_id)
);

CREATE INDEX idx_member_campaigns_campaign ON member_campaigns (campaign_id);
CREATE INDEX idx_member_campaigns_local_fund ON member_campaigns (local_fund_id);
