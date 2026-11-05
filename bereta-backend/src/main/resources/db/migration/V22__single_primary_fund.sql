-- Une association n'a qu'UNE caisse principale (créée avec elle).
-- Correction des données existantes, sans rien supprimer : par association, la caisse principale
-- la plus ancienne est conservée ; les autres y transfèrent leurs caisses locales, campagnes,
-- remises, membres et accès, puis sont archivées. Le détail est gardé dans primary_fund_merges.

ALTER TABLE primary_funds ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL;
ALTER TABLE primary_funds ADD CONSTRAINT ck_primary_funds_status CHECK (status IN ('ACTIVE', 'ARCHIVED'));

CREATE TABLE primary_fund_merges (
    duplicate_id UUID PRIMARY KEY REFERENCES primary_funds (id),
    keeper_id UUID NOT NULL REFERENCES primary_funds (id),
    merged_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO primary_fund_merges (duplicate_id, keeper_id)
SELECT p.id, (SELECT k.id FROM primary_funds k WHERE k.association_id = p.association_id
              ORDER BY k.created_at, k.id LIMIT 1)
FROM primary_funds p
WHERE p.id <> (SELECT k.id FROM primary_funds k WHERE k.association_id = p.association_id
               ORDER BY k.created_at, k.id LIMIT 1);

UPDATE local_funds SET primary_fund_id =
    (SELECT m.keeper_id FROM primary_fund_merges m WHERE m.duplicate_id = local_funds.primary_fund_id)
WHERE primary_fund_id IN (SELECT duplicate_id FROM primary_fund_merges);

UPDATE campaigns SET primary_fund_id =
    (SELECT m.keeper_id FROM primary_fund_merges m WHERE m.duplicate_id = campaigns.primary_fund_id)
WHERE primary_fund_id IN (SELECT duplicate_id FROM primary_fund_merges);

UPDATE cash_remittances SET primary_fund_id =
    (SELECT m.keeper_id FROM primary_fund_merges m WHERE m.duplicate_id = cash_remittances.primary_fund_id)
WHERE primary_fund_id IN (SELECT duplicate_id FROM primary_fund_merges);

UPDATE fund_memberships SET primary_fund_id =
    (SELECT m.keeper_id FROM primary_fund_merges m WHERE m.duplicate_id = fund_memberships.primary_fund_id)
WHERE primary_fund_id IN (SELECT duplicate_id FROM primary_fund_merges);

UPDATE members SET created_in_primary_fund_id =
    (SELECT m.keeper_id FROM primary_fund_merges m WHERE m.duplicate_id = members.created_in_primary_fund_id)
WHERE created_in_primary_fund_id IN (SELECT duplicate_id FROM primary_fund_merges);

UPDATE primary_funds SET status = 'ARCHIVED'
WHERE id IN (SELECT duplicate_id FROM primary_fund_merges);
