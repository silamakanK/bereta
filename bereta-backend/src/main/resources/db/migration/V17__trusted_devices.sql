-- Appareils de confiance : après un code SMS validé avec « Se souvenir de cet appareil »,
-- la connexion depuis ce navigateur ne redemande pas de code pendant 30 jours.
-- Seule l'empreinte SHA-256 du jeton (cookie HttpOnly) est stockée.
CREATE TABLE trusted_devices (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_accounts (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    device_label VARCHAR(120) NOT NULL,
    ip_creation VARCHAR(45),
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(30),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_trusted_devices_user ON trusted_devices (user_account_id, revoked_at);
