-- Volta a senha: login com apelido + senha (Argon2id). A senha das contas antigas, que ainda estava guardada para o
-- "último login", volta a ser simplesmente a senha. Contas que só têm chave de acesso entram uma vez com ela e criam
-- uma senha; a chave então deixa de valer.
alter table users rename column legacy_password_hash to password_hash;

-- E-mail opcional, só para recuperar a senha e receber avisos de segurança. Cifrado (AES-256-GCM, contexto da linha
-- como dado autenticado), sem índice e sem unicidade: não dá para descobrir se um e-mail tem conta nem procurar por ele.
create table account_emails (
    user_id     uuid        primary key references users (id) on delete cascade,
    email_enc   bytea       not null,
    verified_at timestamptz,
    updated_at  timestamptz not null
);

-- Links de uso único mandados por e-mail (confirmar o endereço, redefinir a senha). Só o SHA-256 do token.
create table account_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    purpose    varchar(20) not null,
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    constraint ck_account_tokens_purpose check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
create unique index ux_account_tokens_hash on account_tokens (token_hash);
create index ix_account_tokens_user on account_tokens (user_id, purpose, created_at);

-- Mudanças sensíveis na conta, mostradas ao dono. Sem IP, aparelho ou local. Guardadas 90 dias.
create table security_events (
    id          uuid        primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    kind        varchar(40) not null,
    occurred_at timestamptz not null
);
create index ix_security_events_user on security_events (user_id, occurred_at desc);
create index ix_security_events_occurred on security_events (occurred_at);
