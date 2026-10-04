-- Papéis do banco do weediário. Roda como superusuário, a cada `docker compose up`, antes do backend (serviço
-- db-roles). É idempotente: numa instalação nova prepara o terreno para o Flyway; numa existente (ou depois de um
-- restore) passa a posse das tabelas para o dono do schema e refaz as permissões.
--
--   weediario        superusuário criado pela imagem do Postgres. Só manutenção e backup; o backend nunca o usa.
--   weediario_owner  dono do schema. Só o Flyway conecta com ele (migrations).
--   weediario_app    o que o backend usa para atender requisições: lê e escreve linhas, nada além disso. Sem DDL, sem
--                    COPY para arquivo/programa, sem ler pg_authid. Uma SQL injection, se um dia existir, fica presa aqui.
--
-- As senhas são definidas por roles.sh, que roda este arquivo.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'weediario_owner') THEN
        CREATE ROLE weediario_owner LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'weediario_app') THEN
        CREATE ROLE weediario_app LOGIN;
    END IF;
END $$;

-- Reafirmados a cada execução, caso alguém tenha mudado à mão.
ALTER ROLE weediario_owner NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS LOGIN;
ALTER ROLE weediario_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT LOGIN;

-- Nenhum dos dois herda outro papel (pg_read_server_files, pg_execute_server_program...).
DO $$
DECLARE
    membership record;
BEGIN
    FOR membership IN
        SELECT g.rolname AS granted, m.rolname AS member
          FROM pg_auth_members a
          JOIN pg_roles g ON g.oid = a.roleid
          JOIN pg_roles m ON m.oid = a.member
         WHERE m.rolname IN ('weediario_owner', 'weediario_app')
    LOOP
        EXECUTE format('REVOKE %I FROM %I', membership.granted, membership.member);
    END LOOP;
END $$;

-- Teto por comando na conexão do backend: uma consulta patológica (ou forçada por um atacante) não segura o banco.
ALTER ROLE weediario_app SET statement_timeout = '30s';
ALTER ROLE weediario_app SET lock_timeout = '10s';
ALTER ROLE weediario_app SET idle_in_transaction_session_timeout = '60s';

-- Extensões exigem superusuário em algumas versões: criadas aqui, o "create extension if not exists" das
-- migrations vira nada.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I OWNER TO weediario_owner', current_database());
    EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO weediario_app', current_database());
END $$;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;

ALTER SCHEMA public OWNER TO weediario_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO weediario_app;

-- Instalação antiga ou restore: as tabelas foram criadas pelo superusuário. Passam para o dono do schema (índices e
-- sequências ligadas a colunas acompanham a tabela). Objetos de extensão ficam com quem os criou.
DO $$
DECLARE
    relation record;
BEGIN
    FOR relation IN
        SELECT c.oid::regclass AS name
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
           AND c.relowner <> 'weediario_owner'::regrole
           AND NOT EXISTS (SELECT FROM pg_depend d
                            WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid AND d.deptype IN ('e', 'a', 'i'))
    LOOP
        EXECUTE format('ALTER TABLE %s OWNER TO weediario_owner', relation.name);
    END LOOP;
END $$;

-- Só linhas: sem TRUNCATE, REFERENCES nem TRIGGER.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO weediario_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO weediario_app;
-- O histórico de migrations é do Flyway.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON public.flyway_schema_history FROM weediario_app;
    END IF;
END $$;

-- Tabelas que as próximas migrations criarem já nascem acessíveis ao backend.
ALTER DEFAULT PRIVILEGES FOR ROLE weediario_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO weediario_app;
ALTER DEFAULT PRIVILEGES FOR ROLE weediario_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO weediario_app;
