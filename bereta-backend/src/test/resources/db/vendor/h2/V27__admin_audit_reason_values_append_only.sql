-- Équivalent H2 (tests) : mêmes colonnes ; JSON au lieu de JSONB, pas de trigger (vérifié sur PostgreSQL).
ALTER TABLE admin_audit_log ADD COLUMN reason VARCHAR(500);
ALTER TABLE admin_audit_log ADD COLUMN before_values JSON;
ALTER TABLE admin_audit_log ADD COLUMN after_values JSON;
ALTER TABLE admin_audit_log ADD COLUMN ip_address VARCHAR(45);
CREATE INDEX idx_admin_audit_actor ON admin_audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_admin_audit_association ON admin_audit_log (association_id, created_at DESC);
