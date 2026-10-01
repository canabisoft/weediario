-- Sem e-mail: o login é só apelido + senha, e a recuperação de conta usa uma frase de 12 palavras (BIP-39).
-- A frase é gerada e derivada no navegador; o servidor guarda só o Argon2 do derivado (nunca as palavras).

drop index if exists ux_users_email;
alter table users drop column email;

-- Nulo em contas criadas antes desta versão: elas geram a frase no próximo login.
alter table users add column recovery_hash varchar(255);
