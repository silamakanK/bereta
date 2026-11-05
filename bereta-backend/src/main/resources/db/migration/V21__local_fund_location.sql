-- Maquette « Créer une caisse – étape 1 » : adresse / lieu de réunion et objectif,
-- tous deux facultatifs. Aucune donnée existante n'est modifiée.
ALTER TABLE local_funds ADD COLUMN location VARCHAR(200);
ALTER TABLE local_funds ADD COLUMN description VARCHAR(500);
