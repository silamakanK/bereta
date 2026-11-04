CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_accounts (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_password_reset_user_unused ON password_reset_tokens (user_account_id, used_at, expires_at);
