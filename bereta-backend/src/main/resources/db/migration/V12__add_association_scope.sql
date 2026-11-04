CREATE TABLE associations (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    contact_email VARCHAR(320),
    contact_phone VARCHAR(30),
    address VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE association_memberships (
    id UUID PRIMARY KEY,
    association_id UUID NOT NULL REFERENCES associations (id),
    user_account_id UUID NOT NULL REFERENCES user_accounts (id),
    role VARCHAR(30) NOT NULL CHECK (role = 'RESPONSABLE_ASSOCIATION'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_association_membership UNIQUE (association_id, user_account_id)
);

CREATE INDEX idx_association_memberships_user_active
    ON association_memberships (user_account_id, active);

ALTER TABLE primary_funds ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE primary_funds ADD CONSTRAINT uq_primary_funds_id_association UNIQUE (id, association_id);

ALTER TABLE local_funds ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE local_funds ADD CONSTRAINT uq_local_funds_id_association UNIQUE (id, association_id);
ALTER TABLE local_funds ADD CONSTRAINT fk_local_funds_primary_association
    FOREIGN KEY (primary_fund_id, association_id)
    REFERENCES primary_funds (id, association_id);

ALTER TABLE campaigns ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE campaigns ADD CONSTRAINT uq_campaigns_id_association UNIQUE (id, association_id);
ALTER TABLE campaigns ADD CONSTRAINT fk_campaigns_primary_association
    FOREIGN KEY (primary_fund_id, association_id)
    REFERENCES primary_funds (id, association_id);

ALTER TABLE members ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE members ADD CONSTRAINT uq_members_id_association UNIQUE (id, association_id);
ALTER TABLE members ADD CONSTRAINT fk_members_created_primary_association
    FOREIGN KEY (created_in_primary_fund_id, association_id)
    REFERENCES primary_funds (id, association_id);
ALTER TABLE members ADD CONSTRAINT fk_members_created_local_association
    FOREIGN KEY (created_in_local_fund_id, association_id)
    REFERENCES local_funds (id, association_id);

ALTER TABLE member_campaigns ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE member_campaigns ADD CONSTRAINT fk_member_campaigns_member_association
    FOREIGN KEY (member_id, association_id) REFERENCES members (id, association_id);
ALTER TABLE member_campaigns ADD CONSTRAINT fk_member_campaigns_campaign_association
    FOREIGN KEY (campaign_id, association_id) REFERENCES campaigns (id, association_id);
ALTER TABLE member_campaigns ADD CONSTRAINT fk_member_campaigns_local_association
    FOREIGN KEY (local_fund_id, association_id) REFERENCES local_funds (id, association_id);
ALTER TABLE member_campaigns ADD CONSTRAINT uq_member_campaign_id_association
    UNIQUE (id, association_id);

ALTER TABLE payments ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE payments ADD CONSTRAINT fk_payments_enrollment_association
    FOREIGN KEY (member_campaign_id, association_id)
    REFERENCES member_campaigns (id, association_id);
ALTER TABLE payments ADD CONSTRAINT fk_payments_local_association
    FOREIGN KEY (local_fund_id, association_id)
    REFERENCES local_funds (id, association_id);

ALTER TABLE cash_remittances ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE cash_remittances ADD CONSTRAINT fk_remittances_primary_association
    FOREIGN KEY (primary_fund_id, association_id)
    REFERENCES primary_funds (id, association_id);
ALTER TABLE cash_remittances ADD CONSTRAINT fk_remittances_local_association
    FOREIGN KEY (local_fund_id, association_id)
    REFERENCES local_funds (id, association_id);

ALTER TABLE fund_memberships ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE fund_memberships ADD CONSTRAINT fk_fund_memberships_primary_association
    FOREIGN KEY (primary_fund_id, association_id)
    REFERENCES primary_funds (id, association_id);
ALTER TABLE fund_memberships ADD CONSTRAINT fk_fund_memberships_local_association
    FOREIGN KEY (local_fund_id, association_id)
    REFERENCES local_funds (id, association_id);
ALTER TABLE fund_memberships DROP CONSTRAINT uq_fund_memberships_user_fund;
ALTER TABLE fund_memberships ADD CONSTRAINT uq_fund_memberships_user_local
    UNIQUE (user_account_id, local_fund_id);

ALTER TABLE fund_invitations ADD COLUMN association_id UUID NOT NULL REFERENCES associations (id);
ALTER TABLE fund_invitations ADD CONSTRAINT fk_fund_invitations_local_association
    FOREIGN KEY (local_fund_id, association_id)
    REFERENCES local_funds (id, association_id);
