-- Senha de conta antiga não fica para sempre: depois de 6 meses sem uso, é apagada se a conta já tem frase de
-- recuperação (a pessoa entra recuperando a conta pela frase, que gera uma chave de acesso). Contas sem frase mantêm
-- a senha, porque sem ela não teriam nenhum jeito de voltar.

alter table users drop constraint ck_users_credential;
alter table users add constraint ck_users_credential
    check (access_key_hash is not null or legacy_password_hash is not null or recovery_hash is not null);

-- Ações da moderação no catálogo (corrigir, verificar, juntar, recusar, aprovar/recusar representante) passam a ficar
-- registradas, como as decisões sobre conteúdo. Só inserção; guardado 5 anos.
create table catalog_audit (
    id           uuid         primary key,
    action       varchar(30)  not null,
    item_kind    varchar(20)  not null,
    item_id      uuid         not null,
    item_name    varchar(120),
    related_id   uuid,
    moderator_id uuid         references users (id) on delete set null,
    created_at   timestamptz  not null,
    constraint ck_catalog_audit_action check (action in ('CORRECTED', 'VERIFIED', 'MERGED', 'REJECTED',
        'REPRESENTATIVE_APPROVED', 'REPRESENTATIVE_REVOKED')),
    constraint ck_catalog_audit_kind check (item_kind in ('STRAIN', 'BREEDER', 'BRAND', 'PRODUCT'))
);
create index ix_catalog_audit_created on catalog_audit (created_at);
