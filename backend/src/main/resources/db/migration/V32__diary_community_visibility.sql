-- Nova visibilidade "COMMUNITY": o diário aparece para quem está logado (feed, perfil e rankings) e some para quem
-- não está. PUBLIC continua visível para todo mundo; PRIVATE, só para o dono.

alter table grow_diaries drop constraint ck_diaries_visibility;
alter table grow_diaries add constraint ck_diaries_visibility
    check (visibility in ('PUBLIC', 'COMMUNITY', 'PRIVATE'));

-- Os índices parciais do feed passam a cobrir também COMMUNITY: o feed logado filtra por PUBLIC+COMMUNITY e o feed
-- deslogado por PUBLIC, e o índice mais abrangente serve aos dois.
drop index ix_grow_diaries_public_feed;
drop index ix_grow_diaries_feed_recent;
drop index ix_grow_diaries_feed_stage;
drop index ix_grow_diaries_feed_medium;

create index ix_grow_diaries_public_feed on grow_diaries (created_at desc)
    where visibility in ('PUBLIC', 'COMMUNITY');
create index ix_grow_diaries_feed_recent
    on grow_diaries ((coalesce(last_update_on, started_on)) desc, updated_at desc)
    where visibility in ('PUBLIC', 'COMMUNITY');
create index ix_grow_diaries_feed_stage on grow_diaries (current_stage)
    where visibility in ('PUBLIC', 'COMMUNITY');
create index ix_grow_diaries_feed_medium on grow_diaries (medium)
    where visibility in ('PUBLIC', 'COMMUNITY');
