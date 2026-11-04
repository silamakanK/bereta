-- Supabase expose le schéma public via son API de données (PostgREST) aux rôles
-- anon et authenticated, avec tous les droits par défaut. L'application n'utilise
-- que JDBC avec le rôle propriétaire : on retire ces droits et on active RLS
-- (sans politique, donc tout refuser) sur chaque table. Le propriétaire n'est pas
-- concerné par RLS, l'application continue de fonctionner.
-- Les rôles n'existent pas hors Supabase (PostgreSQL local, CI) : ils sont ignorés.
-- flyway_schema_history est exclue de RLS : Flyway la verrouille pendant la migration
-- (ALTER TABLE y attendrait indéfiniment) ; le REVOKE suffit à la fermer.
DO $$
DECLARE
    api_role text;
    t record;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', api_role);
        END IF;
    END LOOP;

    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
               AND tablename <> 'flyway_schema_history' LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
    END LOOP;
END
$$;
