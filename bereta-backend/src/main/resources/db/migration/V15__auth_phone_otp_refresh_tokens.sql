-- Téléphone comme identifiant principal (E.164), e-mail facultatif.
ALTER TABLE user_accounts ALTER COLUMN email DROP NOT NULL;
ALTER TABLE user_accounts ALTER COLUMN email_normalized DROP NOT NULL;
ALTER TABLE user_accounts ADD COLUMN phone_e164 VARCHAR(16);
CREATE UNIQUE INDEX uq_user_accounts_phone ON user_accounts (phone_e164);
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_identifier
    CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL);

-- Les comptes existants restent actifs ; ils ajouteront leur téléphone à la prochaine connexion.
ALTER TABLE user_accounts ADD COLUMN status VARCHAR(30) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_status
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE'));
ALTER TABLE user_accounts ADD COLUMN phone_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_accounts ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_accounts ADD COLUMN terms_accepted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_accounts ADD COLUMN failed_login_count INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE user_accounts ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_accounts ADD COLUMN must_change_password BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE user_accounts ADD COLUMN token_version INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE user_accounts ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;

-- Codes à usage unique (stockés en HMAC-SHA256, jamais en clair).
-- challenge_id regroupe un code et ses renvois successifs.
CREATE TABLE otp_codes (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL,
    user_account_id UUID NOT NULL REFERENCES user_accounts (id),
    type VARCHAR(20) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER DEFAULT 0 NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_otp_codes_type CHECK (type IN ('LOGIN', 'PHONE_VERIFY', 'EMAIL_VERIFY', 'PASSWORD_RESET')),
    CONSTRAINT ck_otp_codes_channel CHECK (channel IN ('SMS', 'EMAIL'))
);

CREATE INDEX idx_otp_codes_challenge ON otp_codes (challenge_id, created_at);
CREATE INDEX idx_otp_codes_user_type ON otp_codes (user_account_id, type, created_at);

-- Refresh tokens (empreinte SHA-256), rotation par famille.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_accounts (id),
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_agent VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(30)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_account_id, revoked_at);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

-- Journal des événements d'authentification (jamais de mot de passe, code ni jeton).
-- Pas de clé étrangère : il est écrit dans une transaction séparée pendant que le compte
-- est verrouillé (ou pas encore validé à l'inscription).
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY,
    user_account_id UUID,
    event VARCHAR(40) NOT NULL,
    identifier_hint VARCHAR(64),
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    details VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_audit_user ON auth_audit_log (user_account_id, created_at);
CREATE INDEX idx_auth_audit_created ON auth_audit_log (created_at);
