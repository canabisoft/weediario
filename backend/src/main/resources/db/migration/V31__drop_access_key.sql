-- Fim da chave de acesso: toda conta entra com apelido + senha. As contas que ainda não tinham criado senha (só
-- chave, ou só a frase de recuperação) são apagadas; estamos em fase de testes e elas não teriam como entrar.
-- As fotos que ficarem sem dono são recolhidas pela varredura de imagens órfãs.
delete from users where password_hash is null;

alter table users drop constraint ck_users_credential;
drop index if exists ux_users_access_key;
alter table users drop column access_key_hash;
alter table users alter column password_hash set not null;

delete from security_events where kind = 'PASSWORD_CREATED_FROM_ACCESS_KEY';
