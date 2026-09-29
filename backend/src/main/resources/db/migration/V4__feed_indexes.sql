-- Ordenação padrão do feed (update mais recente primeiro), só para diários públicos.
create index ix_grow_diaries_feed_recent
    on grow_diaries ((coalesce(last_update_on, started_on)) desc, updated_at desc)
    where visibility = 'PUBLIC';

-- Filtros mais comuns do feed.
create index ix_grow_diaries_feed_stage on grow_diaries (current_stage) where visibility = 'PUBLIC';
create index ix_grow_diaries_feed_medium on grow_diaries (medium) where visibility = 'PUBLIC';

-- Capa do card: fotos do update mais recente de cada diário.
create index ix_diary_update_photos_update on diary_update_photos (update_id, position);
