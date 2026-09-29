-- Colheita registrada ao concluir o diário (ou depois da cura): base do "gramas por planta".
alter table grow_diaries
    add column harvest_dry_grams integer check (harvest_dry_grams between 1 and 100000),
    add column harvest_plants    integer check (harvest_plants between 1 and 500),
    add constraint ck_diaries_harvest_pair check ((harvest_dry_grams is null) = (harvest_plants is null)),
    add constraint ck_diaries_harvest_completed check (harvest_dry_grams is null or status = 'COMPLETED');
