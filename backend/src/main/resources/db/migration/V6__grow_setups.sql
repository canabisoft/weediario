create table grow_setups (
    id             uuid          primary key,
    owner_id       uuid          not null references users (id) on delete cascade,
    name           varchar(60)   not null,
    environment    varchar(20)   not null,
    tent_width_cm  integer       check (tent_width_cm between 10 and 1000),
    tent_depth_cm  integer       check (tent_depth_cm between 10 and 1000),
    tent_height_cm integer       check (tent_height_cm between 10 and 1000),
    lighting       varchar(120),
    light_wattage  integer       check (light_wattage between 1 and 5000),
    medium         varchar(20),
    nutrients      varchar(120),
    ventilation    varchar(120),
    notes          varchar(1000),
    created_at     timestamptz   not null,
    updated_at     timestamptz   not null,
    constraint ck_setups_environment check (environment in ('INDOOR', 'OUTDOOR', 'GREENHOUSE')),
    constraint ck_setups_medium check (medium is null or medium in ('LIVING_SOIL', 'MINERAL', 'INERT'))
);

create index ix_grow_setups_owner on grow_setups (owner_id);

-- Cópia congelada do setup usado no cultivo (não é chave estrangeira: o setup pode ser editado ou apagado depois).
alter table grow_diaries add column setup_snapshot jsonb;
