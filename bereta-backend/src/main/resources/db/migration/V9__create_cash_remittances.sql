CREATE TABLE cash_remittances (
    id UUID PRIMARY KEY,
    primary_fund_id UUID NOT NULL REFERENCES primary_funds (id),
    local_fund_id UUID NOT NULL REFERENCES local_funds (id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    transferred_on DATE NOT NULL,
    remittance_method VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    declared_by_user_id UUID NOT NULL REFERENCES user_accounts (id),
    declared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_by_user_id UUID REFERENCES user_accounts (id),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    cancelled_by_user_id UUID REFERENCES user_accounts (id),
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(500),
    CONSTRAINT ck_cash_remittance_status CHECK (status IN ('EN_ATTENTE', 'CONFIRMEE', 'ANNULEE')),
    CONSTRAINT ck_cash_remittance_audit CHECK (
        (status = 'EN_ATTENTE' AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL
            AND cancelled_by_user_id IS NULL AND cancelled_at IS NULL)
        OR (status = 'CONFIRMEE' AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL
            AND cancelled_by_user_id IS NULL AND cancelled_at IS NULL)
        OR (status = 'ANNULEE' AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL
            AND cancelled_by_user_id IS NOT NULL AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL)
    )
);

CREATE INDEX idx_cash_remittances_primary_status ON cash_remittances (primary_fund_id, status, declared_at DESC);
CREATE INDEX idx_cash_remittances_local_status ON cash_remittances (local_fund_id, status, declared_at DESC);
