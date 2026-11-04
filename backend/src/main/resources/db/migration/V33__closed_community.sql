-- Comunidade fechada: não existe mais diário aberto para visitantes. O que era PUBLIC vira COMMUNITY (continua
-- compartilhado, agora só com quem tem conta); PRIVATE segue só para o dono. Diário retido pela moderação é gravado
-- como PRIVATE e volta a COMMUNITY quando liberado.

update grow_diaries set visibility = 'COMMUNITY' where visibility = 'PUBLIC';

alter table grow_diaries drop constraint ck_diaries_visibility;
alter table grow_diaries add constraint ck_diaries_visibility
    check (visibility in ('COMMUNITY', 'PRIVATE'));

drop index ix_grow_diaries_public_feed;
drop index ix_grow_diaries_feed_recent;
drop index ix_grow_diaries_feed_stage;
drop index ix_grow_diaries_feed_medium;

create index ix_grow_diaries_community_feed on grow_diaries (created_at desc)
    where visibility = 'COMMUNITY';
create index ix_grow_diaries_feed_recent
    on grow_diaries ((coalesce(last_update_on, started_on)) desc, updated_at desc)
    where visibility = 'COMMUNITY';
create index ix_grow_diaries_feed_stage on grow_diaries (current_stage)
    where visibility = 'COMMUNITY';
create index ix_grow_diaries_feed_medium on grow_diaries (medium)
    where visibility = 'COMMUNITY';
