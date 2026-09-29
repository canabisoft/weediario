create table strains (
    id          uuid        primary key,
    name        varchar(80) not null,
    breeder     varchar(80),
    name_key    varchar(80) not null,
    breeder_key varchar(80) not null,
    created_at  timestamptz not null
);

-- Identidade da genética no catálogo: nome + breeder normalizados.
create unique index ux_strains_key on strains (name_key, breeder_key);

create table reviews (
    id              uuid           primary key,
    strain_id       uuid           not null references strains (id),
    diary_id        uuid           not null references grow_diaries (id) on delete cascade,
    author_id       uuid           not null references users (id) on delete cascade,
    vigor           smallint       not null check (vigor between 1 and 5),
    pest_resistance smallint       not null check (pest_resistance between 1 and 5),
    ease_of_growth  smallint       not null check (ease_of_growth between 1 and 5),
    yield_rating    smallint       not null check (yield_rating between 1 and 5),
    terpene_profile smallint       not null check (terpene_profile between 1 and 5),
    report          varchar(10000) not null,
    created_at      timestamptz    not null
);

-- Uma review por cultivo.
create unique index ux_reviews_diary on reviews (diary_id);
create index ix_reviews_strain_recent on reviews (strain_id, created_at desc);
