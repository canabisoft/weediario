-- Sem senha: login com apelido + chave de acesso gerada pelo servidor. O banco guarda só o SHA-256 da chave
-- (único por conta). A frase de 12 palavras gera uma chave nova quando a pessoa perde a dela.

alter table users add column access_key_hash varchar(64);
create unique index ux_users_access_key on users (access_key_hash);

-- Contas anteriores à chave entram uma última vez com a senha; nesse login recebem a chave e a senha é apagada.
alter table users rename column password_hash to legacy_password_hash;
alter table users alter column legacy_password_hash drop not null;

alter table users add constraint ck_users_credential
    check (access_key_hash is not null or legacy_password_hash is not null);
