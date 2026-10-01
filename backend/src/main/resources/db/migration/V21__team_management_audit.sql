-- Gestão da equipe pelo admin: promover a moderador e remover da equipe entram no mesmo log de moderação
-- (só inserção, 5 anos), com o motivo STAFF_CHANGE.

alter table moderation_log drop constraint ck_moderation_log_action;
alter table moderation_log add constraint ck_moderation_log_action check (action in ('CONTENT_REMOVED',
    'CONTENT_RESTORED', 'USER_SUSPENDED', 'USER_BANNED', 'USER_REINSTATED', 'ROLE_GRANTED', 'ROLE_REVOKED'));

alter table moderation_log drop constraint ck_moderation_log_reason;
alter table moderation_log add constraint ck_moderation_log_reason check (reason in ('ILLEGAL_COMMERCE',
    'TRAFFICKING_APOLOGY', 'INAPPROPRIATE', 'SPAM', 'OTHER', 'UNFOUNDED', 'STAFF_CHANGE'));

-- A lista da equipe e as métricas filtram por papel e status.
create index if not exists ix_users_role on users (role) where role <> 'GROWER';
