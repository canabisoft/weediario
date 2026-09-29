alter table grow_diaries
    add column current_stage  varchar(20) not null default 'GERMINATION',
    add column last_update_on date,
    add constraint ck_diaries_current_stage
        check (current_stage in ('GERMINATION', 'VEGETATIVE', 'FLOWERING', 'DRYING_CURING'));

update grow_diaries set current_stage = 'VEGETATIVE' where seed_sex = 'CLONE';
alter table grow_diaries alter column current_stage drop default;

-- Sem hora, local ou dispositivo: só a data do cultivo e as métricas informadas pelo grower.
create table diary_updates (
    id                  uuid          primary key,
    diary_id            uuid          not null references grow_diaries (id) on delete cascade,
    stage               varchar(20)   not null,
    recorded_on         date          not null,
    note                varchar(5000),
    temperature_celsius numeric(4, 1) check (temperature_celsius between -10 and 60),
    relative_humidity   numeric(4, 1) check (relative_humidity between 0 and 100),
    watering_ph         numeric(4, 2) check (watering_ph between 0 and 14),
    plant_height_cm     numeric(5, 1) check (plant_height_cm between 0 and 1000),
    created_at          timestamptz   not null,
    constraint ck_updates_stage check (stage in ('GERMINATION', 'VEGETATIVE', 'FLOWERING', 'DRYING_CURING'))
);

create index ix_diary_updates_timeline on diary_updates (diary_id, recorded_on desc, created_at desc);

create table diary_update_photos (
    update_id uuid     not null references diary_updates (id) on delete cascade,
    position  integer  not null,
    image_id  uuid     not null references images (id),
    primary key (update_id, position)
);

-- Uma imagem só pode estar em um único update.
create unique index ux_diary_update_photos_image on diary_update_photos (image_id);
