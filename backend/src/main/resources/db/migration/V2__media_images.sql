-- Somente dados gerados pela plataforma após a sanitização. Nada da foto original (EXIF, nome do arquivo, hash).
create table images (
    id         uuid        primary key,
    owner_id   uuid        not null references users (id) on delete cascade,
    width      integer     not null check (width > 0),
    height     integer     not null check (height > 0),
    status     varchar(20) not null,
    created_at timestamptz not null,
    constraint ck_images_status check (status in ('PENDING', 'ATTACHED'))
);

create index ix_images_owner_pending on images (owner_id) where status = 'PENDING';
create index ix_images_pending_created on images (created_at) where status = 'PENDING';
