-- Journal d'audit administrateur : motif, valeurs avant/après (JSONB, jamais de secret), adresse IP.
-- Les entrées antérieures restent lisibles (sans motif).
ALTER TABLE admin_audit_log ADD COLUMN reason VARCHAR(500);
ALTER TABLE admin_audit_log ADD COLUMN before_values JSONB;
ALTER TABLE admin_audit_log ADD COLUMN after_values JSONB;
ALTER TABLE admin_audit_log ADD COLUMN ip_address VARCHAR(45);
CREATE INDEX idx_admin_audit_actor ON admin_audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_admin_audit_association ON admin_audit_log (association_id, created_at DESC);

-- En ajout seul : toute modification ou suppression est refusée par la base elle-même.
CREATE FUNCTION admin_audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'admin_audit_log est en ajout seul (% refusé)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_audit_log_append_only
    BEFORE UPDATE OR DELETE ON admin_audit_log
    FOR EACH ROW EXECUTE FUNCTION admin_audit_log_append_only();
