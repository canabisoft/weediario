create table seedbanks (
    id         uuid        primary key,
    name       varchar(80) not null,
    name_key   varchar(80) not null,
    created_at timestamptz not null
);

create unique index ux_seedbanks_key on seedbanks (name_key);

-- Avaliação opcional do banco de sementes junto com a review do cultivo (as três notas juntas ou nenhuma).
alter table reviews
    add column seedbank_id          uuid references seedbanks (id),
    add column seedbank_germination smallint check (seedbank_germination between 1 and 5),
    add column seedbank_stability   smallint check (seedbank_stability between 1 and 5),
    add column seedbank_service     smallint check (seedbank_service between 1 and 5),
    add constraint ck_reviews_seedbank_complete check (
        (seedbank_id is null and seedbank_germination is null and seedbank_stability is null and seedbank_service is null)
        or (seedbank_id is not null and seedbank_germination is not null
            and seedbank_stability is not null and seedbank_service is not null));

create index ix_reviews_seedbank_recent on reviews (seedbank_id, created_at desc) where seedbank_id is not null;
