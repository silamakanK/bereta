-- Une seule caisse principale active par association.
CREATE UNIQUE INDEX uq_primary_funds_active_association ON primary_funds (association_id) WHERE status = 'ACTIVE';
