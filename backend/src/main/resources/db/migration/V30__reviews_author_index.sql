-- O ranking de growers e o perfil público contam as avaliações de cada autor.
create index ix_reviews_author on reviews (author_id) where not hidden;
