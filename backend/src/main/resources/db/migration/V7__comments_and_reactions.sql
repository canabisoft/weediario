create table diary_comments (
    id         uuid          primary key,
    diary_id   uuid          not null references grow_diaries (id) on delete cascade,
    author_id  uuid          not null references users (id) on delete cascade,
    body       varchar(2000) not null,
    hidden     boolean       not null default false,
    created_at timestamptz   not null
);

create index ix_diary_comments_thread on diary_comments (diary_id, created_at) where not hidden;
create index ix_diary_comments_rate_limit on diary_comments (author_id, created_at);

-- Denúncias guardam só o pseudônimo HMAC de quem denunciou (não o id do usuário).
create table comment_reports (
    comment_id uuid        not null references diary_comments (id) on delete cascade,
    reporter   varchar(64) not null,
    created_at timestamptz not null,
    primary key (comment_id, reporter)
);

-- Reações anônimas: o "reactor" é um pseudônimo HMAC diferente para cada diário, não o id do usuário.
create table diary_reactions (
    diary_id   uuid        not null references grow_diaries (id) on delete cascade,
    reactor    varchar(64) not null,
    type       varchar(20) not null,
    created_at timestamptz not null,
    primary key (diary_id, reactor, type),
    constraint ck_reactions_type check (type in ('FIRE', 'HERB', 'CLAP'))
);

create index ix_diary_reactions_diary on diary_reactions (diary_id);
