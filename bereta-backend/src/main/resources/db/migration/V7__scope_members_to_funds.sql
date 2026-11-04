ALTER TABLE members ADD COLUMN created_in_primary_fund_id UUID REFERENCES primary_funds (id);
ALTER TABLE members ADD COLUMN created_in_local_fund_id UUID REFERENCES local_funds (id);

CREATE INDEX idx_members_created_in_primary ON members (created_in_primary_fund_id);
CREATE INDEX idx_members_created_in_local ON members (created_in_local_fund_id);
