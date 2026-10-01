-- Termos de Uso, filtro anti-comércio e moderação unificada.
-- Privacidade: o aceite dos termos guarda só versão e data/hora (nada de IP, user-agent ou aparelho);
-- denúncias guardam só um pseudônimo de quem denunciou; o log de moderação não guarda o conteúdo removido,
-- só um hash dele.

-- ---------------------------------------------------------------------------------------------- termos
alter table users
    add column terms_version     varchar(10),
    add column terms_accepted_at timestamptz,
    add column profile_hidden    boolean not null default false,
    add constraint ck_users_terms check ((terms_version is null) = (terms_accepted_at is null));

alter table users drop constraint ck_users_status;
alter table users add constraint ck_users_status check (status in ('ACTIVE', 'SUSPENDED', 'BANNED'));

-- ------------------------------------------------------------------------- conteúdo retido pela moderação
-- Diário e receita retidos ficam privados (e travados) até a moderação decidir; review fica oculto.
alter table grow_diaries add column moderation_hold boolean not null default false;
alter table recipes add column moderation_hold boolean not null default false;
alter table reviews add column hidden boolean not null default false;

-- --------------------------------------------------------------------------------- denúncias unificadas
create table content_reports (
    target_type varchar(20) not null,
    target_id   uuid        not null,
    -- Pseudônimo HMAC de (quem denunciou, alvo): impede denúncia repetida sem identificar ninguém.
    reporter    varchar(64) not null,
    reason      varchar(30) not null,
    created_at  timestamptz not null,
    primary key (target_type, target_id, reporter),
    constraint ck_content_reports_type
        check (target_type in ('COMMENT', 'DIARY', 'REVIEW', 'RECIPE', 'RECIPE_COMMENT', 'PROFILE')),
    constraint ck_content_reports_reason
        check (reason in ('ILLEGAL_COMMERCE', 'TRAFFICKING_APOLOGY', 'INAPPROPRIATE', 'SPAM', 'OTHER'))
);

create index ix_content_reports_created on content_reports (created_at);

-- Denúncias de comentário já eram pseudônimos com a mesma derivação: migram como estão.
insert into content_reports (target_type, target_id, reporter, reason, created_at)
select 'COMMENT', comment_id, reporter, 'OTHER', created_at from comment_reports;

-- As de comentário de receita guardavam o id de quem denunciou: migram sem essa identificação.
insert into content_reports (target_type, target_id, reporter, reason, created_at)
select 'RECIPE_COMMENT', comment_id, encode(sha256(convert_to(gen_random_uuid()::text, 'UTF8')), 'hex'), 'OTHER', reported_at
  from recipe_comment_reports;

drop table comment_reports;
drop table recipe_comment_reports;

-- Conteúdo aguardando decisão da moderação (por denúncias ou pelo filtro automático).
create table moderation_holds (
    target_type varchar(20)  not null,
    target_id   uuid         not null,
    cause       varchar(12)  not null,
    detail      varchar(120),
    held_at     timestamptz  not null,
    primary key (target_type, target_id),
    constraint ck_moderation_holds_cause check (cause in ('REPORTS', 'AUTO_FILTER'))
);

insert into moderation_holds (target_type, target_id, cause, held_at)
select 'COMMENT', id, 'REPORTS', created_at from diary_comments where hidden;
insert into moderation_holds (target_type, target_id, cause, held_at)
select 'RECIPE_COMMENT', id, 'REPORTS', created_at from recipe_comments where hidden;

-- ------------------------------------------------------------------------------ log de moderação
-- Só inserção (a aplicação não altera nem apaga), guardado por 5 anos para defesa em processo
-- (LGPD, art. 7º, VI). Sem IP, sem quem denunciou e sem o conteúdo: só o hash SHA-256 do texto removido.
create table moderation_log (
    id                uuid         primary key,
    target_type       varchar(20)  not null,
    target_id         uuid         not null,
    reported_user_id  uuid         references users (id) on delete set null,
    reported_username varchar(30),
    moderator_id      uuid         references users (id) on delete set null,
    action            varchar(20)  not null,
    reason            varchar(30)  not null,
    note              varchar(500),
    content_sha256    varchar(64),
    created_at        timestamptz  not null,
    constraint ck_moderation_log_action check (action in ('CONTENT_REMOVED', 'CONTENT_RESTORED', 'USER_SUSPENDED',
        'USER_BANNED', 'USER_REINSTATED')),
    constraint ck_moderation_log_reason check (reason in ('ILLEGAL_COMMERCE', 'TRAFFICKING_APOLOGY', 'INAPPROPRIATE',
        'SPAM', 'OTHER', 'UNFOUNDED'))
);

create index ix_moderation_log_user on moderation_log (reported_user_id, created_at);
create index ix_moderation_log_created on moderation_log (created_at);
