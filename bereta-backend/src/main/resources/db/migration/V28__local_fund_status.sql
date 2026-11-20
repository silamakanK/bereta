-- Espace administrateur (lot 2) : une caisse locale peut être archivée (lecture seule), jamais supprimée.
-- Les caisses existantes restent actives.
ALTER TABLE local_funds ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE local_funds ADD CONSTRAINT ck_local_funds_status CHECK (status IN ('ACTIVE', 'ARCHIVED'));
CREATE INDEX idx_local_funds_status ON local_funds (status);
