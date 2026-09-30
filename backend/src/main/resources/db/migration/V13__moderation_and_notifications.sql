-- Apelidos de breeders, marcas e produtos juntados por moderadores: quem digitar o nome antigo cai no principal.
create table breeder_aliases (
    alias_key  varchar(80) primary key,
    breeder_id uuid        not null references breeders (id) on delete cascade
);

create table brand_aliases (
    alias_key varchar(80) primary key,
    brand_id  uuid        not null references brands (id) on delete cascade
);

create table product_aliases (
    brand_id   uuid        not null references brands (id) on delete cascade,
    alias_key  varchar(80) not null,
    product_id uuid        not null references products (id) on delete cascade,
    primary key (brand_id, alias_key)
);

-- Fila de moderação do catálogo: itens da comunidade ainda não verificados.
create index ix_strains_pending on strains (created_at desc) where status = 'UNVERIFIED' and source = 'COMMUNITY';
create index ix_breeders_pending on breeders (created_at desc) where status = 'UNVERIFIED' and source = 'COMMUNITY';
create index ix_brands_pending on brands (created_at desc) where status = 'UNVERIFIED' and source = 'COMMUNITY';
create index ix_products_pending on products (created_at desc) where status = 'UNVERIFIED' and source = 'COMMUNITY';

create index ix_comment_reports_comment on comment_reports (comment_id);

-- Notificações. Reações e novos seguidores são anônimos (não guardamos quem reagiu nem quem seguiu).
-- Reações, seguidores e updates de quem você segue se acumulam numa notificação só (com contador) enquanto não lidas.
-- Guardadas por no máximo 90 dias.
create table notifications (
    id           uuid        primary key,
    recipient_id uuid        not null references users (id) on delete cascade,
    type         varchar(20) not null,
    diary_id     uuid        references grow_diaries (id) on delete cascade,
    actor_id     uuid        references users (id) on delete cascade,
    occurrences  integer     not null default 1 check (occurrences >= 1),
    created_at   timestamptz not null,
    read_at      timestamptz,
    constraint ck_notifications_type check (type in ('COMMENT', 'REACTION', 'NEW_FOLLOWER', 'FOLLOWED_UPDATE')),
    constraint ck_notifications_anonymous check (type not in ('REACTION', 'NEW_FOLLOWER') or actor_id is null)
);

create index ix_notifications_inbox on notifications (recipient_id, created_at desc);
create index ix_notifications_unread on notifications (recipient_id) where read_at is null;
create index ix_notifications_created on notifications (created_at);
create unique index ux_notifications_unread_aggregate
    on notifications (recipient_id, type, coalesce(diary_id, '00000000-0000-0000-0000-000000000000'::uuid))
    where read_at is null and type in ('REACTION', 'NEW_FOLLOWER', 'FOLLOWED_UPDATE');
