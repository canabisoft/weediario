-- Controle de força bruta por conta. A chave é um HMAC do e-mail digitado: a tabela não guarda e-mails nem IPs.
create table login_attempts (
    attempt_key       varchar(64) primary key,
    failures          integer     not null,
    window_started_at timestamptz not null,
    locked_until      timestamptz,
    lock_count        integer     not null,
    updated_at        timestamptz not null
);

create index ix_login_attempts_updated on login_attempts (updated_at);
