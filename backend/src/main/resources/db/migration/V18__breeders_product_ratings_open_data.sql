-- Programa de breeders (sementes "em teste"), nota de produtos na review pós-colheita e banco aberto de strains.

-- ------------------------------------------------------------------------------- nota dos produtos
-- Opcional na review do cultivo: uma nota de 1 a 5 por produto usado no diário.
create table review_products (
    review_id  uuid     not null references reviews (id) on delete cascade,
    product_id uuid     not null references products (id),
    rating     smallint not null check (rating between 1 and 5),
    primary key (review_id, product_id)
);
create index ix_review_products_product on review_products (product_id);

-- ------------------------------------------------------------------------- strains em teste
-- TESTING: cadastrada por um representante do breeder; só vira oficial (VERIFIED, com promoted_at) quando
-- cultivadores de fora do breeder a testam e avaliam. BREEDER: origem do cadastro.
alter table strains drop constraint ck_strains_status;
alter table strains drop constraint ck_strains_source;
alter table strains
    add column promoted_at timestamptz,
    add constraint ck_strains_status check (status in ('UNVERIFIED', 'VERIFIED', 'TESTING')),
    add constraint ck_strains_source check (source in ('DATASET', 'COMMUNITY', 'BREEDER'));

create index ix_strains_testing on strains (breeder_id) where status = 'TESTING';

-- ---------------------------------------------------------------------- representantes de breeders
-- Quem pode cadastrar sementes de um breeder. A nota (prova de vínculo, ex.: site oficial) só é vista pela
-- moderação; quem representa um breeder nunca é exibido publicamente. Recusar ou revogar apaga a linha.
create table breeder_representatives (
    breeder_id   uuid         not null references breeders (id) on delete cascade,
    user_id      uuid         not null references users (id) on delete cascade,
    status       varchar(10)  not null,
    note         varchar(500),
    requested_at timestamptz  not null,
    decided_at   timestamptz,
    primary key (breeder_id, user_id),
    constraint ck_breeder_representatives_status check (status in ('PENDING', 'APPROVED')),
    constraint ck_breeder_representatives_decided check ((status = 'APPROVED') = (decided_at is not null))
);
create index ix_breeder_representatives_user on breeder_representatives (user_id);
create index ix_breeder_representatives_pending on breeder_representatives (requested_at) where status = 'PENDING';
