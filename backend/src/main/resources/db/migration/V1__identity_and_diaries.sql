-- Privacidade por design: nenhuma tabela contém IP, geolocalização, cidade/estado, user-agent ou identificador de dispositivo.

create table users (
    id            uuid         primary key,
    username      varchar(30)  not null,
    email         varchar(254) not null,
    password_hash varchar(255) not null,
    role          varchar(20)  not null,
    status        varchar(20)  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint ck_users_role   check (role in ('GROWER', 'MODERATOR', 'ADMIN')),
    constraint ck_users_status check (status in ('ACTIVE', 'SUSPENDED'))
);

create unique index ux_users_username on users (lower(username));
-- O e-mail já é normalizado para minúsculas pelo domínio.
create unique index ux_users_email on users (email);

create table refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    family_id  uuid        not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    revoked_at timestamptz
);

create unique index ux_refresh_tokens_hash on refresh_tokens (token_hash);
create index ix_refresh_tokens_family on refresh_tokens (family_id);
create index ix_refresh_tokens_user on refresh_tokens (user_id);

create table grow_diaries (
    id             uuid         primary key,
    owner_id       uuid         not null references users (id) on delete cascade,
    title          varchar(100) not null,
    strain_name    varchar(80)  not null,
    breeder        varchar(80),
    flowering_type varchar(20)  not null,
    seed_sex       varchar(20)  not null,
    medium         varchar(20)  not null,
    status         varchar(20)  not null,
    visibility     varchar(20)  not null,
    started_on     date         not null,
    ended_on       date,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    constraint ck_diaries_flowering  check (flowering_type in ('PHOTOPERIOD', 'AUTOFLOWER')),
    constraint ck_diaries_seed_sex   check (seed_sex in ('REGULAR', 'FEMINIZED', 'CLONE')),
    constraint ck_diaries_medium     check (medium in ('LIVING_SOIL', 'MINERAL', 'INERT')),
    constraint ck_diaries_status     check (status in ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    constraint ck_diaries_visibility check (visibility in ('PUBLIC', 'PRIVATE')),
    constraint ck_diaries_dates      check (ended_on is null or ended_on >= started_on)
);

create index ix_grow_diaries_owner_status on grow_diaries (owner_id, status);
create index ix_grow_diaries_public_feed on grow_diaries (created_at desc) where visibility = 'PUBLIC';
