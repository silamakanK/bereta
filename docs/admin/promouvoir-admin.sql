-- =====================================================================================================
-- Promotion d'un compte existant en administrateur général BERETA — procédure interne contrôlée.
-- Il n'existe AUCUN endpoint ni écran pour cela : ce script est exécuté à la main dans Supabase
-- (SQL Editor), par une personne habilitée, après vérification de l'identité du titulaire du compte.
--
-- 1. Remplacez les deux valeurs ci-dessous (téléphone au format E.164, ou e-mail) et le motif.
-- 2. Exécutez d'abord la partie « Vérification » : exactement 1 compte actif doit apparaître.
-- 3. Exécutez ensuite la transaction « Promotion ».
-- 4. La personne se reconnecte : son nouveau rôle prend effet à la connexion suivante.
-- =====================================================================================================

-- Vérification
SELECT id, display_name, email, phone_e164, role, enabled
FROM user_accounts
WHERE phone_e164 = '+33600000000' OR lower(email) = lower('admin@example.org');

-- Promotion : une seule instruction, qui ne journalise que si le compte a réellement été promu
-- (entrée sans auteur applicatif : « Procédure interne » dans le journal).
BEGIN;

WITH target AS (
    SELECT id, role AS old_role
    FROM user_accounts
    WHERE (phone_e164 = '+33600000000' OR lower(email) = lower('admin@example.org'))
      AND enabled = TRUE
      AND role <> 'ADMIN'
    FOR UPDATE
), promoted AS (
    UPDATE user_accounts u
    SET role = 'ADMIN',
        token_version = u.token_version + 1      -- ferme les sessions ouvertes avec l'ancien rôle
    FROM target
    WHERE u.id = target.id
    RETURNING u.id, target.old_role
)
INSERT INTO admin_audit_log (id, actor_user_id, action, target_type, target_id, reason,
                             before_values, after_values, created_at)
SELECT gen_random_uuid(), NULL, 'ADMIN_PROMOTED_BY_PROCEDURE', 'USER', id,
       'Motif de la promotion (qui a demandé, qui a vérifié)',
       jsonb_build_object('role', old_role), jsonb_build_object('role', 'ADMIN'), now()
FROM promoted;

-- « INSERT 0 1 » attendu (1 compte promu). Sinon : ROLLBACK; et vérifiez le compte.
COMMIT;   -- ou ROLLBACK; en cas de doute

-- Retrait du rôle (même procédure) : role = 'RESPONSABLE' ou 'TRESORIER', action 'ADMIN_DEMOTED_BY_PROCEDURE'.
-- Garde-fou : ne jamais retirer le dernier administrateur actif :
--   SELECT count(*) FROM user_accounts WHERE role = 'ADMIN' AND enabled = TRUE;   -- doit rester ≥ 1
