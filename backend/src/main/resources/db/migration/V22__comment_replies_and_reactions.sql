-- Respostas (um nível só) e reações anônimas nos comentários de diários e de receitas.
--
-- Respostas: parent_id aponta sempre para um comentário raiz do mesmo diário/receita (a aplicação anexa a resposta
-- de uma resposta ao mesmo comentário raiz). Apagar o comentário raiz apaga as respostas junto (on delete cascade):
-- fora da conversa elas perdem o sentido e não guardamos texto de ninguém sem necessidade.
--
-- Reações: mesma mecânica das reações de diário. O "reactor" é um pseudônimo HMAC derivado de (usuário, comentário),
-- diferente em cada comentário e diferente do pseudônimo de denúncia do mesmo comentário. Não há coluna com o id
-- de quem reagiu nem data da reação (não precisamos dela para nada).

alter table diary_comments add column parent_id uuid references diary_comments (id) on delete cascade;
create index ix_diary_comments_replies on diary_comments (parent_id, created_at) where parent_id is not null;

alter table recipe_comments add column parent_id uuid references recipe_comments (id) on delete cascade;
create index ix_recipe_comments_replies on recipe_comments (parent_id, created_at) where parent_id is not null;

create table diary_comment_reactions (
    comment_id uuid        not null references diary_comments (id) on delete cascade,
    reactor    varchar(64) not null,
    type       varchar(20) not null,
    primary key (comment_id, reactor, type),
    constraint ck_diary_comment_reactions_type check (type in ('FIRE', 'HERB', 'CLAP'))
);

create table recipe_comment_reactions (
    comment_id uuid        not null references recipe_comments (id) on delete cascade,
    reactor    varchar(64) not null,
    type       varchar(20) not null,
    primary key (comment_id, reactor, type),
    constraint ck_recipe_comment_reactions_type check (type in ('FIRE', 'HERB', 'CLAP'))
);

-- Notificação de resposta: avisa o autor do comentário respondido (o apelido de quem respondeu já é público).
-- Respostas em receitas apontam para a receita.
alter table notifications add column recipe_id uuid references recipes (id) on delete cascade;
alter table notifications drop constraint ck_notifications_type;
alter table notifications add constraint ck_notifications_type
    check (type in ('COMMENT', 'REACTION', 'NEW_FOLLOWER', 'FOLLOWED_UPDATE', 'REPLY'));
