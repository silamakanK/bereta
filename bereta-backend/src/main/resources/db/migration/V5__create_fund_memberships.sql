CREATE TABLE fund_memberships (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_accounts (id),
    primary_fund_id UUID NOT NULL REFERENCES primary_funds (id),
    local_fund_id UUID REFERENCES local_funds (id),
    role VARCHAR(30) NOT NULL CHECK (role IN ('RESPONSABLE', 'TRESORIER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_fund_memberships_user_fund UNIQUE (user_account_id, primary_fund_id),
    CONSTRAINT ck_fund_memberships_scope CHECK (
        (role = 'RESPONSABLE' AND local_fund_id IS NULL)
        OR (role = 'TRESORIER' AND local_fund_id IS NOT NULL)
    )
);

CREATE INDEX idx_fund_memberships_user ON fund_memberships (user_account_id, active);
CREATE INDEX idx_fund_memberships_primary ON fund_memberships (primary_fund_id, active);
CREATE INDEX idx_fund_memberships_local ON fund_memberships (local_fund_id, active);

ALTER TABLE members ADD COLUMN created_by_user_id UUID REFERENCES user_accounts (id);
CREATE INDEX idx_members_created_by ON members (created_by_user_id);

