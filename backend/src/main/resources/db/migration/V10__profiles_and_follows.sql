-- Perfil opcional. A foto é uma imagem já sanitizada (sem EXIF) do contexto de mídia.
alter table users
    add column bio             varchar(280),
    add column avatar_image_id uuid references images (id) on delete set null;

-- Quem segue quem. A API expõe só a contagem de seguidores; a lista inteira nunca é pública.
create table grower_follows (
    follower_id uuid        not null references users (id) on delete cascade,
    followee_id uuid        not null references users (id) on delete cascade,
    created_at  timestamptz not null,
    primary key (follower_id, followee_id),
    constraint ck_follows_not_self check (follower_id <> followee_id)
);

create index ix_grower_follows_followee on grower_follows (followee_id);
