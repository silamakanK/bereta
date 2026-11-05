-- Inscriptions et paiements de la caisse principale, statut des campagnes, archivage des membres.
--
-- member_campaigns.association_id et payments.association_id existent déjà (V12), obligatoires et
-- remplis : ils rattachent chaque inscription / paiement à son association, donc à son unique caisse
-- principale (V22). local_fund_id devient facultatif : vide = suivi / encaissé par la caisse principale.
ALTER TABLE member_campaigns ALTER COLUMN local_fund_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN local_fund_id DROP NOT NULL;
CREATE INDEX idx_member_campaigns_association ON member_campaigns (association_id);
CREATE INDEX idx_payments_association ON payments (association_id);

-- Campagne OUVERTE ou CLOTUREE (clôture manuelle par le responsable) ; « en retard » reste calculé avec l'échéance.
ALTER TABLE campaigns ADD COLUMN status VARCHAR(20) DEFAULT 'OUVERTE' NOT NULL;
ALTER TABLE campaigns ADD CONSTRAINT ck_campaigns_status CHECK (status IN ('OUVERTE', 'CLOTUREE'));
ALTER TABLE campaigns ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;

-- Membres archivés : conservés, mais plus inscrits automatiquement aux nouvelles campagnes.
ALTER TABLE members ADD COLUMN active BOOLEAN DEFAULT TRUE NOT NULL;
