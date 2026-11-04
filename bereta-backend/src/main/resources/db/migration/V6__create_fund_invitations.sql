CREATE TABLE fund_invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    local_fund_id UUID NOT NULL REFERENCES local_funds (id),
    invited_by_user_id UUID NOT NULL REFERENCES user_accounts (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_fund_invitations_email ON fund_invitations (email, accepted_at);
