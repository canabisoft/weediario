-- Limpeza do catálogo de genéticas. O dataset importado veio de uma loja de sementes e trazia ruído de vitrine:
--   * "SALE - X - Banco" (ou "SALE - Banco - X - Loja") → junta com a strain X do banco (ou renomeia);
--   * "Banco - X" sem breeder        → separa: strain X do Banco (cria o breeder se preciso);
--   * "X Regular/Feminised Seeds"    → junta com X (ou renomeia);
--   * brindes ("3 Free Seeds - £75 to £100"), pacotes e misturas ("Mixed Pack", "Feminised Mix", "Box - 6 Strains")
--     e produtos de loja (camisetas, canecas, toalhas do "Seed Bank Clothing") → apagados, junto com o "breeder"
--     que era só categoria da loja;
--   * "Linha do Banco - X" já com breeder ("Blimburn America - Gelato") → "X" do mesmo breeder;
--   * nome de banco cadastrado como strain sem breeder ("Valencia Seeds") → apagado;
--   * símbolo de moeda no nome ("Dank Mon£y", "Petro $ OG") → tirado ("Dank Money", "Petro OG"); com preço → apagado.
-- Juntar move diários, reviews e apelidos para a strain principal e guarda o nome antigo como apelido (o mesmo nome
-- nunca volta a virar duplicata). Só é apagado o que ninguém usa; o que estiver em uso é avisado e fica.
-- Mesmas regras de CatalogNames (Java), que barram esses nomes em cadastros novos.

create or replace function pg_temp.catalog_key(raw text) returns text language sql as $$
    select trim(regexp_replace(regexp_replace(lower(unaccent(coalesce(raw, ''))), '[''’`]', '', 'g'), '[^a-z0-9]+', ' ', 'g'))
$$;
create or replace function pg_temp.breeder_key(raw text) returns text language sql as $$
    select trim(regexp_replace(pg_temp.catalog_key(raw), '( seeds? bank| seedbank| seed company| seed co| seeds?)$', ''))
$$;

-- Não é genética: brinde, pacote/kit, preço. (Os padrões espelham CatalogNames.)
create or replace function pg_temp.not_a_genetic(name text) returns boolean language sql as $$
    select name ~* '\mfree\s+seeds?\M'
        or name ~* '\m(mix(ed)?\s*pack|mixpack|mystery\s+pack|collection\s+pack|collectors?''?s?\s+pack|champion\s+pack|storage\s+packs?|femini[sz]ed\s+collection|auto\s+pack\s*#?\d*)\M'
        or name ~ '[$€£¥₿¢]\s*\d' or name ~ '\d\s*[$€£¥₿¢]'
        or name ~* '\m\d+([.,]\d+)?\s*(gbp|usd|eur|brl|euros?|dollars?|reais|libras?)\M'
        or name ~* '\mfemini[sz]ed\s+mix\M|\mmix\s+femini[sz]ed\M|\m\d+\s+strains\M|\manniversary\s+box\M'
        or name ~* '\m(t-?shirts?|tshirts?|sweatshirts?|hoodies?|crewneck|crop\s+top|tote\s+bag|drawstring\s+bag|storage\s+rack|clothing|apparel|merch(andise)?|camisetas?|canecas?)\M'
$$;

create or replace function pg_temp.used(strain uuid) returns boolean language sql as $$
    select exists (select 1 from grow_diaries where strain_id = strain)
        or exists (select 1 from reviews where strain_id = strain)
$$;

-- Junta a duplicata na principal (igual a JdbcCatalogRepository.mergeStrain).
create or replace function pg_temp.merge_strain(dup uuid, target uuid) returns void language plpgsql as $$
begin
    insert into strain_aliases (alias_key, breeder_key, strain_id)
    select name_key, breeder_key, target from strains where id = dup
    on conflict (alias_key, breeder_key) do update set strain_id = excluded.strain_id;
    update strain_aliases set strain_id = target where strain_id = dup;
    update grow_diaries set strain_id = target, breeder_id = (select breeder_id from strains where id = target)
     where strain_id = dup;
    update reviews set strain_id = target where strain_id = dup;
    delete from strains where id = dup;
end $$;

-- Dá o nome limpo (e o breeder) à strain; se já existir uma igual (ou um apelido), junta nela.
create or replace function pg_temp.rehome(strain uuid, new_name text, new_breeder uuid) returns void language plpgsql as $$
declare
    k  text := pg_temp.catalog_key(new_name);
    bk text := coalesce((select name_key from breeders where id = new_breeder), '');
    target uuid;
begin
    if k = '' then
        return;
    end if;
    select id into target from strains where name_key = k and breeder_key = bk and id <> strain limit 1;
    if target is null then
        select strain_id into target from strain_aliases where alias_key = k and breeder_key = bk and strain_id <> strain;
    end if;
    if target is not null then
        perform pg_temp.merge_strain(strain, target);
        return;
    end if;
    -- O nome antigo vira apelido da própria strain (um reimport com o nome sujo cai nela).
    insert into strain_aliases (alias_key, breeder_key, strain_id)
    select name_key, breeder_key, id from strains where id = strain and (name_key <> k or breeder_key <> bk)
    on conflict (alias_key, breeder_key) do nothing;
    update strains
       set name = left(trim(new_name), 80), name_key = k, breeder_id = new_breeder, breeder_key = bk,
           breeder = (select name from breeders where id = new_breeder), updated_at = now()
     where id = strain;
    update grow_diaries set breeder_id = new_breeder where strain_id = strain;
end $$;

create or replace function pg_temp.breeder_for(raw text) returns uuid language plpgsql as $$
declare
    bk text := pg_temp.breeder_key(raw);
    found uuid;
begin
    if bk = '' then
        return null;
    end if;
    select id into found from breeders where name_key = bk;
    if found is null then
        -- Texto truncado pela loja ("Seed City Bulk Canna"): só aceita se o começo bater com um breeder só.
        select min(id::text)::uuid into found from breeders where name_key like bk || '%' having count(*) = 1;
    end if;
    return found;
end $$;

do $$
declare
    r record;
    parts text[];
    clean text;
    breeder uuid;
    twin uuid;
    removed int := 0;
    kept int := 0;
    merged_or_renamed int := 0;
begin
    -- 1. Brindes, pacotes e nomes com preço.
    for r in select id, name from strains where pg_temp.not_a_genetic(name) loop
        if pg_temp.used(r.id) then
            kept := kept + 1;
            raise notice 'Mantida (em uso): %', r.name;
        else
            delete from strains where id = r.id;
            removed := removed + 1;
        end if;
    end loop;

    -- 1b. Tudo de "breeders" que são só categoria da loja (roupa, acessórios, armazenamento): canecas, toalhas...
    for r in select s.id, s.name from strains s join breeders b on b.id = s.breeder_id
              where b.name ~* '\m(clothing|apparel|merch(andise)?|storage|accessories)\M' loop
        if pg_temp.used(r.id) then
            kept := kept + 1;
            raise notice 'Mantida (em uso): %', r.name;
        else
            delete from strains where id = r.id;
            removed := removed + 1;
        end if;
    end loop;

    -- 2. "SALE - X - Banco".
    for r in select id, name, breeder_id from strains where name ~* '^\s*(sale|promo[cç][aã]o|promocao|oferta)\s*[-:–]' loop
        clean := regexp_replace(r.name, '^\s*(sale|promo[cç][aã]o|promocao|oferta)\s*[-:–]\s*', '', 'i');
        parts := regexp_split_to_array(clean, '\s+-\s*');
        -- "SALE - Banco - X - Loja": o primeiro trecho é o banco.
        if array_length(parts, 1) >= 3 and (pg_temp.breeder_for(parts[1]) is not null
                or parts[1] ~* '\m(seeds?|genetics|labs?|seedbank)$') then
            breeder := pg_temp.breeder_for(parts[1]);
            if breeder is null then
                insert into breeders (id, name, name_key, status, source, created_at)
                values (gen_random_uuid(), trim(parts[1]), pg_temp.breeder_key(parts[1]), 'UNVERIFIED', 'DATASET', now())
                on conflict (name_key) do nothing;
                breeder := pg_temp.breeder_for(parts[1]);
            end if;
            clean := trim(parts[2]);
        else
            clean := trim(parts[1]);
            breeder := coalesce(pg_temp.breeder_for(parts[2]), r.breeder_id);
        end if;
        if pg_temp.not_a_genetic(clean) then
            if not pg_temp.used(r.id) then
                delete from strains where id = r.id;
                removed := removed + 1;
            end if;
            continue;
        end if;
        if breeder is null then
            -- Sem breeder: se só existe uma strain com esse nome, é ela.
            select min(id::text)::uuid into twin from strains
             where name_key = pg_temp.catalog_key(clean) and id <> r.id having count(*) = 1;
            if twin is not null then
                perform pg_temp.merge_strain(r.id, twin);
                merged_or_renamed := merged_or_renamed + 1;
                continue;
            end if;
        end if;
        perform pg_temp.rehome(r.id, clean, breeder);
        merged_or_renamed := merged_or_renamed + 1;
    end loop;

    -- 3. "Banco - X" sem breeder, quando o prefixo é um breeder (conhecido, com cara de breeder ou repetido 3+ vezes).
    for r in
        with prefixed as (
            select id, name, trim(split_part(name, ' - ', 1)) as prefix,
                   trim(substr(name, position(' - ' in name) + 3)) as rest
              from strains where breeder_id is null and name ~ '\s-\s')
        select p.* from prefixed p
         where p.rest <> ''
           and (exists (select 1 from breeders b where b.name_key = pg_temp.breeder_key(p.prefix))
                or p.prefix ~* '\m(seeds?|genetics|labs?|seedbank|seed co\.?)$'
                or (select count(*) from prefixed q where q.prefix = p.prefix) >= 3)
    loop
        breeder := pg_temp.breeder_for(r.prefix);
        if breeder is null then
            insert into breeders (id, name, name_key, status, source, created_at)
            values (gen_random_uuid(), r.prefix, pg_temp.breeder_key(r.prefix), 'UNVERIFIED', 'DATASET', now())
            on conflict (name_key) do nothing;
            breeder := pg_temp.breeder_for(r.prefix);
        end if;
        perform pg_temp.rehome(r.id, r.rest, breeder);
        merged_or_renamed := merged_or_renamed + 1;
    end loop;

    -- 3b. "Linha do Banco - X" quando a strain já tem o breeder e o prefixo é o próprio breeder ("Blimburn America - Gelato").
    for r in select s.id, s.breeder_id, trim(substr(s.name, position(' - ' in s.name) + 3)) as rest
               from strains s join breeders b on b.id = s.breeder_id
              where s.name ~ '\s-\s' and length(b.name_key) > 3
                and pg_temp.catalog_key(split_part(s.name, ' - ', 1)) like b.name_key || '%'
                and trim(substr(s.name, position(' - ' in s.name) + 3)) <> '' loop
        perform pg_temp.rehome(r.id, r.rest, r.breeder_id);
        merged_or_renamed := merged_or_renamed + 1;
    end loop;

    -- 3c. Nome de banco cadastrado como strain, sem breeder ("Valencia Seeds").
    for r in select s.id from strains s
              where s.breeder_id is null and exists (select 1 from breeders b where b.name_key = pg_temp.breeder_key(s.name))
                and s.name ~* '\m(seeds?|genetics|seedbank)$' loop
        if not pg_temp.used(r.id) then
            delete from strains where id = r.id;
            removed := removed + 1;
        end if;
    end loop;

    -- 4. "X Regular Seeds", "X Feminised Seeds".
    for r in select id, name, breeder_id from strains
              where name ~* '\s+(regular|femini[sz]ed|fem|autoflowering)\s+seeds?\s*$' loop
        perform pg_temp.rehome(r.id,
                regexp_replace(r.name, '\s+(regular|femini[sz]ed|fem|autoflowering)\s+seeds?\s*$', '', 'i'), r.breeder_id);
        merged_or_renamed := merged_or_renamed + 1;
    end loop;

    -- 5. Símbolo de moeda estilizado (sem preço): "Dank Mon£y" → "Dank Money", "Petro $ OG" → "Petro OG".
    for r in select id, name, breeder_id from strains where name ~ '[$€£¥₿¢]' loop
        clean := regexp_replace(r.name, '\s+[$€£¥₿¢]+\s+', ' ', 'g');
        clean := translate(clean, '£€$¥₿¢', 'eesyb');
        perform pg_temp.rehome(r.id, trim(regexp_replace(clean, '\s+', ' ', 'g')), r.breeder_id);
        merged_or_renamed := merged_or_renamed + 1;
    end loop;

    -- 6. "Breeders" que eram só categoria da loja (roupa, acessórios) e ficaram sem nada.
    delete from breeders b
     where (b.name ~* '\m(clothing|apparel|merch(andise)?|storage|accessories|sale items?)\M')
       and not exists (select 1 from strains s where s.breeder_id = b.id)
       and not exists (select 1 from grow_diaries d where d.breeder_id = b.id)
       and not exists (select 1 from reviews rv where rv.seedbank_id = b.id)
       and not exists (select 1 from breeder_representatives br where br.breeder_id = b.id);

    raise notice 'Catálogo limpo: % apagadas, % juntadas ou renomeadas, % mantidas por estarem em uso',
        removed, merged_or_renamed, kept;
end $$;

drop function pg_temp.breeder_for(text);
drop function pg_temp.rehome(uuid, text, uuid);
drop function pg_temp.merge_strain(uuid, uuid);
drop function pg_temp.used(uuid);
drop function pg_temp.not_a_genetic(text);
