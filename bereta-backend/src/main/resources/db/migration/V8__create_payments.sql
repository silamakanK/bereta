CREATE TABLE payments (
    id UUID PRIMARY KEY,
    member_campaign_id UUID NOT NULL REFERENCES member_campaigns (id),
    local_fund_id UUID NOT NULL REFERENCES local_funds (id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    paid_on DATE NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    recorded_by_user_id UUID NOT NULL REFERENCES user_accounts (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payments_enrollment ON payments (member_campaign_id, paid_on DESC);
CREATE INDEX idx_payments_local_fund ON payments (local_fund_id, paid_on DESC);
