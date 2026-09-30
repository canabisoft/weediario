-- Catálogo da comunidade: strains, breeders, marcas e produtos, sem duplicatas.
-- Busca tolerante a erro de digitação com pg_trgm; chaves normalizadas iguais às do Java (CatalogKey).

create extension if not exists pg_trgm;
create extension if not exists unaccent;

-- Funções só para migrar os dados existentes (removidas no fim). Mesma regra de CatalogKey.of / breederKey.
create function pg_temp.catalog_key(raw text) returns text language sql as $$
    select trim(regexp_replace(regexp_replace(lower(unaccent(coalesce(raw, ''))), '[''’`]', '', 'g'), '[^a-z0-9]+', ' ', 'g'))
$$;
create function pg_temp.breeder_key(raw text) returns text language sql as $$
    select trim(regexp_replace(pg_temp.catalog_key(raw), '( seeds? bank| seedbank| seed company| seed co| seeds?)$', ''))
$$;

-- ---------------------------------------------------------------------------------------------------
-- Breeders (antes "seedbanks", usados só nas reviews)
-- ---------------------------------------------------------------------------------------------------
alter table seedbanks rename to breeders;
alter index ux_seedbanks_key rename to ux_breeders_key;
alter table breeders
    add column status     varchar(20) not null default 'UNVERIFIED',
    add column source     varchar(20) not null default 'COMMUNITY',
    add column created_by uuid references users (id) on delete set null,
    add constraint ck_breeders_status check (status in ('UNVERIFIED', 'VERIFIED')),
    add constraint ck_breeders_source check (source in ('DATASET', 'COMMUNITY'));

drop index ux_breeders_key;
update breeders set name_key = pg_temp.breeder_key(name);
-- Nomes que passaram a colidir com a nova regra viram um só breeder (o mais antigo).
with ranked as (
    select id, first_value(id) over (partition by name_key order by created_at, id) as keeper from breeders
)
update reviews r set seedbank_id = ranked.keeper from ranked where r.seedbank_id = ranked.id and ranked.id <> ranked.keeper;
delete from breeders b using breeders older
 where b.name_key = older.name_key and (older.created_at, older.id) < (b.created_at, b.id);
create unique index ux_breeders_key on breeders (name_key);

-- Breeders citados em strains e diários que ainda não existem no catálogo.
insert into breeders (id, name, name_key, created_at, status, source)
select gen_random_uuid(), min(src.name), pg_temp.breeder_key(src.name), now(), 'UNVERIFIED', 'COMMUNITY'
  from (select breeder as name from strains where breeder is not null
        union all
        select breeder from grow_diaries where breeder is not null) src
 where pg_temp.breeder_key(src.name) <> ''
   and not exists (select 1 from breeders b where b.name_key = pg_temp.breeder_key(src.name))
 group by pg_temp.breeder_key(src.name);

-- ---------------------------------------------------------------------------------------------------
-- Strains: ganham atributos, vínculo com o breeder e origem do dado
-- ---------------------------------------------------------------------------------------------------
alter table strains
    add column breeder_id      uuid references breeders (id),
    add column flowering_type  varchar(20),
    add column dominance       varchar(20),
    add column flowering_speed varchar(20),
    add column seed_types      varchar(20)[] not null default '{}',
    add column flavors         varchar(30)[] not null default '{}',
    add column effects         varchar(30)[] not null default '{}',
    add column genetics        varchar(120),
    add column status          varchar(20) not null default 'UNVERIFIED',
    add column source          varchar(20) not null default 'COMMUNITY',
    add column created_by      uuid references users (id) on delete set null,
    add column updated_at      timestamptz,
    add constraint ck_strains_flowering check (flowering_type is null or flowering_type in ('PHOTOPERIOD', 'AUTOFLOWER')),
    add constraint ck_strains_dominance check (dominance is null or dominance in
        ('INDICA', 'INDICA_DOMINANT', 'HYBRID', 'SATIVA_DOMINANT', 'SATIVA')),
    add constraint ck_strains_speed check (flowering_speed is null or flowering_speed in ('SHORT', 'MEDIUM', 'LONG')),
    add constraint ck_strains_status check (status in ('UNVERIFIED', 'VERIFIED')),
    add constraint ck_strains_source check (source in ('DATASET', 'COMMUNITY'));

drop index ux_strains_key;
update strains set name_key = pg_temp.catalog_key(name), breeder_key = pg_temp.breeder_key(breeder), updated_at = created_at;
with ranked as (
    select id, first_value(id) over (partition by name_key, breeder_key order by created_at, id) as keeper from strains
)
update reviews r set strain_id = ranked.keeper from ranked where r.strain_id = ranked.id and ranked.id <> ranked.keeper;
delete from strains s using strains older
 where s.name_key = older.name_key and s.breeder_key = older.breeder_key
   and (older.created_at, older.id) < (s.created_at, s.id);
update strains s set breeder_id = b.id from breeders b where s.breeder_key <> '' and b.name_key = s.breeder_key;
alter table strains alter column updated_at set not null;
create unique index ux_strains_key on strains (name_key, breeder_key);

-- Strains dos diários existentes que ainda não estão no catálogo.
insert into strains (id, name, breeder, name_key, breeder_key, breeder_id, created_at, updated_at, status, source)
select gen_random_uuid(), min(d.strain_name), min(d.breeder),
       pg_temp.catalog_key(d.strain_name), pg_temp.breeder_key(d.breeder), min(b.id::text)::uuid, now(), now(),
       'UNVERIFIED', 'COMMUNITY'
  from grow_diaries d
  left join breeders b on b.name_key = pg_temp.breeder_key(d.breeder) and pg_temp.breeder_key(d.breeder) <> ''
 where not exists (select 1 from strains s
                    where s.name_key = pg_temp.catalog_key(d.strain_name)
                      and s.breeder_key = pg_temp.breeder_key(d.breeder))
 group by pg_temp.catalog_key(d.strain_name), pg_temp.breeder_key(d.breeder);

-- Apelidos: nomes alternativos que levam à mesma strain (ex.: após juntar duplicatas).
create table strain_aliases (
    alias_key   varchar(80) not null,
    breeder_key varchar(80) not null,
    strain_id   uuid        not null references strains (id) on delete cascade,
    primary key (alias_key, breeder_key)
);

-- ---------------------------------------------------------------------------------------------------
-- Diários passam a apontar para o catálogo (o nome digitado continua como registro histórico)
-- ---------------------------------------------------------------------------------------------------
alter table grow_diaries
    add column strain_id  uuid references strains (id),
    add column breeder_id uuid references breeders (id);

update grow_diaries d
   set strain_id = s.id, breeder_id = s.breeder_id
  from strains s
 where s.name_key = pg_temp.catalog_key(d.strain_name) and s.breeder_key = pg_temp.breeder_key(d.breeder);

create index ix_grow_diaries_strain on grow_diaries (strain_id);
create index ix_grow_diaries_breeder on grow_diaries (breeder_id);

-- ---------------------------------------------------------------------------------------------------
-- Marcas e produtos (fertilizantes, aditivos, substratos...)
-- ---------------------------------------------------------------------------------------------------
create table brands (
    id         uuid        primary key,
    name       varchar(80) not null,
    name_key   varchar(80) not null,
    status     varchar(20) not null default 'UNVERIFIED',
    source     varchar(20) not null default 'COMMUNITY',
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null,
    constraint ck_brands_status check (status in ('UNVERIFIED', 'VERIFIED')),
    constraint ck_brands_source check (source in ('DATASET', 'COMMUNITY'))
);
create unique index ux_brands_key on brands (name_key);

create table products (
    id         uuid        primary key,
    brand_id   uuid        not null references brands (id),
    name       varchar(80) not null,
    name_key   varchar(80) not null,
    category   varchar(20) not null,
    status     varchar(20) not null default 'UNVERIFIED',
    source     varchar(20) not null default 'COMMUNITY',
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null,
    constraint ck_products_category check (category in ('NUTRIENT', 'ADDITIVE', 'SUBSTRATE', 'PEST_CONTROL', 'OTHER')),
    constraint ck_products_status check (status in ('UNVERIFIED', 'VERIFIED')),
    constraint ck_products_source check (source in ('DATASET', 'COMMUNITY'))
);
create unique index ux_products_key on products (brand_id, name_key);

create table grow_setup_products (
    setup_id   uuid    not null references grow_setups (id) on delete cascade,
    position   integer not null,
    product_id uuid    not null references products (id),
    primary key (setup_id, position)
);

-- Produtos usados no cultivo (copiados do setup ao criar o diário): base do ranking de marcas e produtos.
create table diary_products (
    diary_id   uuid not null references grow_diaries (id) on delete cascade,
    product_id uuid not null references products (id),
    primary key (diary_id, product_id)
);
create index ix_diary_products_product on diary_products (product_id);

-- ---------------------------------------------------------------------------------------------------
-- Busca por semelhança (autocomplete e "você quis dizer?")
-- ---------------------------------------------------------------------------------------------------
create index ix_strains_name_trgm on strains using gin (name_key gin_trgm_ops);
create index ix_breeders_name_trgm on breeders using gin (name_key gin_trgm_ops);
create index ix_brands_name_trgm on brands using gin (name_key gin_trgm_ops);
create index ix_products_name_trgm on products using gin (name_key gin_trgm_ops);
