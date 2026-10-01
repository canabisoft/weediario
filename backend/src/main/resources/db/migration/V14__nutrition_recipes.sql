-- Receitas de nutrição (mineral ou orgânica) criadas pelos growers.
-- Uma receita rende um lote ("para X litros" de solução, de substrato ou de chá) e lista ingredientes com quantidade.
-- Ingrediente de marca aponta para o catálogo (conta em "Produtos"); sal/insumo genérico fica só pelo nome.

create table recipes (
    id              uuid         primary key,
    owner_id        uuid         not null references users (id) on delete cascade,
    name            varchar(80)  not null,
    kind            varchar(20)  not null,
    application     varchar(20)  not null,
    stage           varchar(20),
    batch_liters    numeric(8, 2) not null,
    target_ec       numeric(4, 2),
    target_ph       numeric(3, 1),
    notes           varchar(2000),
    visibility      varchar(20)  not null,
    -- Receita replicada de outra (atribuição). Se a original for apagada, a cópia continua existindo.
    replicated_from uuid         references recipes (id) on delete set null,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null,
    constraint ck_recipes_kind check (kind in ('MINERAL', 'ORGANIC')),
    constraint ck_recipes_application check (application in ('FERTIGATION', 'SOIL_MIX', 'TEA', 'FOLIAR', 'TOP_DRESS')),
    constraint ck_recipes_stage check (stage is null or stage in ('VEGETATIVE', 'FLOWERING', 'ALL')),
    constraint ck_recipes_visibility check (visibility in ('PUBLIC', 'PRIVATE')),
    constraint ck_recipes_batch check (batch_liters > 0),
    constraint ck_recipes_ec check (target_ec is null or target_ec between 0 and 10),
    constraint ck_recipes_ph check (target_ph is null or target_ph between 0 and 14)
);
create index ix_recipes_owner on recipes (owner_id, updated_at desc);
create index ix_recipes_public on recipes (kind, updated_at desc) where visibility = 'PUBLIC';
create index ix_recipes_replicated_from on recipes (replicated_from);

create table recipe_ingredients (
    recipe_id  uuid           not null references recipes (id) on delete cascade,
    position   integer        not null,
    product_id uuid           references products (id),
    -- Nome e marca congelados no momento em que a receita foi salva (como ProductSnapshot nos diários).
    name       varchar(80)    not null,
    brand      varchar(80),
    quantity   numeric(10, 3) not null,
    unit       varchar(10)    not null,
    primary key (recipe_id, position),
    constraint ck_recipe_ingredients_quantity check (quantity > 0),
    constraint ck_recipe_ingredients_unit check (unit in ('G', 'MG', 'KG', 'ML', 'L', 'TSP', 'TBSP', 'UNIT'))
);
create index ix_recipe_ingredients_product on recipe_ingredients (product_id) where product_id is not null;

-- Receitas usadas num cultivo (ex.: uma de vega e uma de flora). Base para "cultivos com esta receita".
create table diary_recipes (
    diary_id  uuid not null references grow_diaries (id) on delete cascade,
    recipe_id uuid not null references recipes (id) on delete cascade,
    primary key (diary_id, recipe_id)
);
create index ix_diary_recipes_recipe on diary_recipes (recipe_id);
