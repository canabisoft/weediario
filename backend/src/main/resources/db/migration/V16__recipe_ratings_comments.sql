-- Avaliação (1 a 5 estrelas) e comentários das receitas públicas.
-- Uma avaliação por grower e receita (pode ser trocada). O autor não avalia a própria receita.

create table recipe_ratings (
    recipe_id  uuid        not null references recipes (id) on delete cascade,
    rater_id   uuid        not null references users (id) on delete cascade,
    stars      smallint    not null,
    rated_at   timestamptz not null,
    primary key (recipe_id, rater_id),
    constraint ck_recipe_ratings_stars check (stars between 1 and 5)
);
create index ix_recipe_ratings_rater on recipe_ratings (rater_id);

-- Mesmas regras dos comentários de diário: só apelido do autor, nada de IP/aparelho, ocultado após denúncias.
create table recipe_comments (
    id         uuid          primary key,
    recipe_id  uuid          not null references recipes (id) on delete cascade,
    author_id  uuid          not null references users (id) on delete cascade,
    body       varchar(1000) not null,
    hidden     boolean       not null default false,
    created_at timestamptz   not null
);
create index ix_recipe_comments_recipe on recipe_comments (recipe_id, created_at);
create index ix_recipe_comments_author on recipe_comments (author_id, created_at);

create table recipe_comment_reports (
    comment_id  uuid        not null references recipe_comments (id) on delete cascade,
    reporter_id uuid        not null references users (id) on delete cascade,
    reported_at timestamptz not null,
    primary key (comment_id, reporter_id)
);
