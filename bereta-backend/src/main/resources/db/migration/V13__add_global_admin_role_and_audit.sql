ALTER TABLE user_accounts DROP CONSTRAINT ck_user_accounts_role;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_role
    CHECK (role IN ('RESPONSABLE', 'TRESORIER', 'ADMIN'));

CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES user_accounts (id),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    association_id UUID REFERENCES associations (id),
    details VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_audit_created_at ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_target ON admin_audit_log (target_type, target_id, created_at DESC);
