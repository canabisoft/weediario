-- Desafios anti-robô do cadastro já resolvidos: cada solução vale uma vez só. Nada sobre quem resolveu; a linha some
-- quando o desafio vence.
create table used_register_challenges (
    challenge  varchar(64) primary key,
    expires_at timestamptz not null
);
create index ix_used_register_challenges_expires on used_register_challenges (expires_at);
