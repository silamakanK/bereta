-- Équivalent H2 (tests) de l'index partiel PostgreSQL : H2 ne connaît pas « WHERE » sur un index.
ALTER TABLE primary_funds ADD COLUMN active_association_id UUID
    GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN association_id END);
CREATE UNIQUE INDEX uq_primary_funds_active_association ON primary_funds (active_association_id);
