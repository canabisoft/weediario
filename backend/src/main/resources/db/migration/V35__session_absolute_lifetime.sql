-- Sessão com vida máxima: a rotação do refresh token renova a validade, mas nunca além de 90 dias desde o login.
-- Sem isso, quem usa o app todo dia ficaria com a mesma sessão para sempre (e um cookie roubado também).
alter table refresh_tokens add column family_started_at timestamptz;

update refresh_tokens t
   set family_started_at = f.started_at
  from (select family_id, min(created_at) as started_at from refresh_tokens group by family_id) f
 where f.family_id = t.family_id;

alter table refresh_tokens alter column family_started_at set not null;
