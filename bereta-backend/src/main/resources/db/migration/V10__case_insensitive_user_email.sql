ALTER TABLE user_accounts ADD COLUMN email_normalized VARCHAR(320);

UPDATE user_accounts SET email_normalized = LOWER(email);

ALTER TABLE user_accounts ALTER COLUMN email_normalized SET NOT NULL;

CREATE UNIQUE INDEX uq_user_accounts_email_normalized ON user_accounts (email_normalized);
