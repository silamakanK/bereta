CREATE TABLE primary_funds (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    objective VARCHAR(180) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE local_funds (
    id UUID PRIMARY KEY,
    primary_fund_id UUID NOT NULL REFERENCES primary_funds (id),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_local_funds_primary_fund ON local_funds (primary_fund_id);

CREATE TABLE campaigns (
    id UUID PRIMARY KEY,
    primary_fund_id UUID NOT NULL REFERENCES primary_funds (id),
    name VARCHAR(120) NOT NULL,
    campaign_type VARCHAR(20) NOT NULL CHECK (campaign_type IN ('ANNUAL', 'EVENT')),
    campaign_year SMALLINT,
    expected_amount NUMERIC(12, 2),
    due_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_annual_campaign_year CHECK (campaign_type <> 'ANNUAL' OR campaign_year IS NOT NULL)
);

CREATE INDEX idx_campaigns_primary_fund ON campaigns (primary_fund_id);
