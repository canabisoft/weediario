-- Programa de breeders aberto a breeders independentes (quem cria genéticas por conta própria), não só bancos.

-- Tipo do breeder: banco de sementes (empresa/marca) ou breeder independente. Nulo = não informado.
alter table breeders add column kind varchar(20);
alter table breeders add constraint ck_breeders_kind check (kind is null or kind in ('SEEDBANK', 'INDEPENDENT'));
update breeders set kind = 'SEEDBANK' where source = 'DATASET';

-- No pedido, quem pede diz o que representa (a moderação confere e, ao aprovar, o breeder recebe esse tipo).
-- Breeder independente pode, se quiser, aparecer como criador(a) das genéticas (padrão: anônimo).
alter table breeder_representatives
    add column claimed_kind  varchar(20) not null default 'SEEDBANK',
    add column public_credit boolean     not null default false,
    add constraint ck_breeder_representatives_kind check (claimed_kind in ('SEEDBANK', 'INDEPENDENT')),
    add constraint ck_breeder_representatives_credit check (not public_credit or claimed_kind = 'INDEPENDENT');

-- Geração da genética (estabilidade): F1, F2, F3 ou mais, S1, backcross, linhagem estável, poli-híbrido.
alter table strains add column generation varchar(20);
alter table strains add constraint ck_strains_generation check (generation is null or generation in
    ('F1', 'F2', 'F3_PLUS', 'S1', 'BX', 'IBL', 'POLYHYBRID'));
