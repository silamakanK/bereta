-- Inscription en deux temps : téléphone + mot de passe d'abord, prénom et nom ensuite
-- (écran « Complète ton profil »). Le nom affiché devient donc facultatif.
ALTER TABLE user_accounts ALTER COLUMN display_name DROP NOT NULL;
