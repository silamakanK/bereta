-- Authentification simplifiée : plus de code (OTP) ni de vérification d'e-mail ou de téléphone,
-- plus d'appareils de confiance. Les comptes sont actifs dès l'inscription.
DROP TABLE trusted_devices;
DROP TABLE otp_codes;

UPDATE user_accounts SET status = 'ACTIVE' WHERE status <> 'ACTIVE';
ALTER TABLE user_accounts DROP CONSTRAINT ck_user_accounts_status;
ALTER TABLE user_accounts DROP COLUMN status;
ALTER TABLE user_accounts DROP COLUMN phone_verified_at;
ALTER TABLE user_accounts DROP COLUMN email_verified_at;

-- password_reset_tokens est conservée : le mot de passe oublié par e-mail sera réactivé plus tard.
