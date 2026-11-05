-- ===== CASCADE : données propres au compte =====
ALTER TABLE refresh_tokens DROP CONSTRAINT refresh_tokens_user_account_id_fkey,
  ADD CONSTRAINT refresh_tokens_user_account_id_fkey FOREIGN KEY (user_account_id) REFERENCES user_accounts(id) ON DELETE CASCADE;

ALTER TABLE otp_codes DROP CONSTRAINT otp_codes_user_account_id_fkey,
  ADD CONSTRAINT otp_codes_user_account_id_fkey FOREIGN KEY (user_account_id) REFERENCES user_accounts(id) ON DELETE CASCADE;

ALTER TABLE password_reset_tokens DROP CONSTRAINT password_reset_tokens_user_account_id_fkey,
  ADD CONSTRAINT password_reset_tokens_user_account_id_fkey FOREIGN KEY (user_account_id) REFERENCES user_accounts(id) ON DELETE CASCADE;

ALTER TABLE association_memberships DROP CONSTRAINT association_memberships_user_account_id_fkey,
  ADD CONSTRAINT association_memberships_user_account_id_fkey FOREIGN KEY (user_account_id) REFERENCES user_accounts(id) ON DELETE CASCADE;

ALTER TABLE fund_memberships DROP CONSTRAINT fund_memberships_user_account_id_fkey,
  ADD CONSTRAINT fund_memberships_user_account_id_fkey FOREIGN KEY (user_account_id) REFERENCES user_accounts(id) ON DELETE CASCADE;

-- ===== SET NULL : traces d'actions (colonnes rendues optionnelles) =====
ALTER TABLE members ALTER COLUMN created_by_user_id DROP NOT NULL,
  DROP CONSTRAINT members_created_by_user_id_fkey,
  ADD CONSTRAINT members_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL;

ALTER TABLE fund_invitations ALTER COLUMN invited_by_user_id DROP NOT NULL,
  DROP CONSTRAINT fund_invitations_invited_by_user_id_fkey,
  ADD CONSTRAINT fund_invitations_invited_by_user_id_fkey FOREIGN KEY (invited_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL;

ALTER TABLE payments ALTER COLUMN recorded_by_user_id DROP NOT NULL,
  DROP CONSTRAINT payments_recorded_by_user_id_fkey,
  ADD CONSTRAINT payments_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL;

ALTER TABLE cash_remittances
  ALTER COLUMN declared_by_user_id DROP NOT NULL,
  DROP CONSTRAINT cash_remittances_declared_by_user_id_fkey,
  ADD CONSTRAINT cash_remittances_declared_by_user_id_fkey FOREIGN KEY (declared_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL,
  DROP CONSTRAINT cash_remittances_confirmed_by_user_id_fkey,
  ADD CONSTRAINT cash_remittances_confirmed_by_user_id_fkey FOREIGN KEY (confirmed_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL,
  DROP CONSTRAINT cash_remittances_cancelled_by_user_id_fkey,
  ADD CONSTRAINT cash_remittances_cancelled_by_user_id_fkey FOREIGN KEY (cancelled_by_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL;

ALTER TABLE admin_audit_log ALTER COLUMN actor_user_id DROP NOT NULL,
  DROP CONSTRAINT admin_audit_log_actor_user_id_fkey,
  ADD CONSTRAINT admin_audit_log_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL;