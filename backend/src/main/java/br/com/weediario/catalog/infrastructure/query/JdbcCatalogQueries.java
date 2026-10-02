package br.com.weediario.catalog.infrastructure.query;

import br.com.weediario.catalog.application.query.CatalogQueries;
import br.com.weediario.catalog.domain.model.CatalogKey;
import br.com.weediario.catalog.domain.model.TestingProgress;
import br.com.weediario.catalog.infrastructure.persistence.TestingProgressSql;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.sql.Types;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas do catálogo em SQL parametrizado. Busca: começo do nome primeiro, depois semelhança (pg_trgm),
 * desempatando pelo mais usado. Uso = diários públicos de contas ativas.
 */
@Component
public class JdbcCatalogQueries implements CatalogQueries {

    /** Diários que contam para uso e rankings. */
    private static final String PUBLIC_DIARIES = """
            (grow_diaries d join users du on du.id = d.owner_id and du.status = 'ACTIVE' and d.visibility = 'PUBLIC')""";

    private static final String STRAIN_REVIEWS = """
            left join lateral (
                select count(*) as review_count,
                       round(avg((rv.vigor + rv.pest_resistance + rv.ease_of_growth + rv.yield_rating + rv.terpene_profile) / 5.0), 1) as overall
                  from reviews rv join users ru on ru.id = rv.author_id and ru.status = 'ACTIVE' and not rv.hidden
                 where rv.strain_id = s.id) r on true
            """;

    /** Nota média dos produtos nas reviews pós-colheita (mesmo critério das notas de strain). */
    private static final String PRODUCT_RATINGS = """
            left join lateral (
                select count(*) as review_count, round(avg(rp.rating), 1) as rating
                  from review_products rp
                  join reviews rv on rv.id = rp.review_id and not rv.hidden
                  join users ru on ru.id = rv.author_id and ru.status = 'ACTIVE'
                 where rp.product_id = p.id) pr on true
            """;

    /** Strains do banco oficial: tudo menos as sementes ainda em teste. */
    private static final String OFFICIAL = "s.status <> 'TESTING'";

    private static final double MATCH = 0.35;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCatalogQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------ autocomplete

    @Override
    public List<Suggestion> suggestStrains(String query, UUID breederId, int limit) {
        MapSqlParameterSource params = search(query).addValue("limit", limit).addValue("breederId", breederId, Types.OTHER);
        return jdbc.query("""
                select s.id, s.name, b.id as parent_id, b.name as parent_name, null as category, s.status, s.source,
                       (select count(*) from %s where d.strain_id = s.id) as usage
                  from strains s left join breeders b on b.id = s.breeder_id
                 where (s.name_key like :prefix or word_similarity(:key, s.name_key) >= :match)
                   and (cast(:breederId as uuid) is null or s.breeder_id = :breederId)
                 order by (s.name_key like :prefix) desc, word_similarity(:key, s.name_key) desc, usage desc, s.name
                 limit :limit""".formatted(PUBLIC_DIARIES), params, (rs, n) -> suggestion(rs));
    }

    @Override
    public List<Suggestion> suggestBreeders(String query, int limit) {
        return jdbc.query("""
                select b.id, b.name, null as parent_id, null as parent_name, null as category, b.status, b.source,
                       (select count(*) from strains s where s.breeder_id = b.id) as usage
                  from breeders b
                 where b.name_key like :prefix or word_similarity(:key, b.name_key) >= :match
                 order by (b.name_key like :prefix) desc, word_similarity(:key, b.name_key) desc, usage desc, b.name
                 limit :limit""", search(query).addValue("limit", limit), (rs, n) -> suggestion(rs));
    }

    @Override
    public List<Suggestion> suggestBrands(String query, int limit) {
        return jdbc.query("""
                select br.id, br.name, null as parent_id, null as parent_name, null as category, br.status, br.source,
                       (select count(*) from products p where p.brand_id = br.id) as usage
                  from brands br
                 where br.name_key like :prefix or word_similarity(:key, br.name_key) >= :match
                 order by (br.name_key like :prefix) desc, word_similarity(:key, br.name_key) desc, usage desc, br.name
                 limit :limit""", search(query).addValue("limit", limit), (rs, n) -> suggestion(rs));
    }

    @Override
    public List<Suggestion> suggestProducts(String query, UUID brandId, String category, int limit) {
        MapSqlParameterSource params = search(query).addValue("limit", limit)
                .addValue("brandId", brandId, Types.OTHER).addValue("category", category);
        return jdbc.query("""
                select p.id, p.name, br.id as parent_id, br.name as parent_name, p.category, p.status, p.source,
                       (select count(*) from diary_products dp join %s on d.id = dp.diary_id
                         where dp.product_id = p.id) as usage
                  from products p join brands br on br.id = p.brand_id
                 where (p.name_key like :prefix or word_similarity(:key, p.name_key) >= :match
                        or br.name_key like :prefix)
                   and (cast(:brandId as uuid) is null or p.brand_id = :brandId)
                   and (cast(:category as varchar) is null or p.category = :category)
                 order by (p.name_key like :prefix) desc, word_similarity(:key, p.name_key) desc, usage desc, p.name
                 limit :limit""".formatted(PUBLIC_DIARIES), params, (rs, n) -> suggestion(rs));
    }

    // -------------------------------------------------------------------------------------- listagens

    @Override
    public PageResult<StrainListItem> searchStrains(String query, PageQuery page) {
        MapSqlParameterSource params = paged(query, page);
        String filter = " where " + OFFICIAL + (query == null ? ""
                : " and (s.name_key like :prefix or word_similarity(:key, s.name_key) >= :match or b.name_key like :prefix)");
        String order = query == null
                ? " order by diaries desc, review_count desc, s.name"
                : " order by (s.name_key like :prefix) desc, word_similarity(:key, s.name_key) desc, diaries desc, s.name";
        Long total = jdbc.queryForObject("select count(*) from strains s left join breeders b on b.id = s.breeder_id"
                + filter, params, Long.class);
        List<StrainListItem> items = jdbc.query("""
                select s.id, s.name, b.name as breeder, b.id as breeder_id, s.flowering_type, s.dominance, s.status, s.source,
                       (select count(*) from %s where d.strain_id = s.id) as diaries, r.review_count, r.overall
                  from strains s left join breeders b on b.id = s.breeder_id
                """.formatted(PUBLIC_DIARIES) + STRAIN_REVIEWS + filter + order + " limit :limit offset :offset",
                params,
                (rs, n) -> new StrainListItem(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("breeder"),
                        rs.getObject("breeder_id", UUID.class), rs.getString("flowering_type"), rs.getString("dominance"),
                        rs.getString("status"), rs.getString("source"), rs.getLong("diaries"), rs.getLong("review_count"),
                        rs.getBigDecimal("overall")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public PageResult<BreederListItem> searchBreeders(String query, PageQuery page) {
        MapSqlParameterSource params = paged(query, page);
        String filter = query == null ? "" : " where b.name_key like :prefix or word_similarity(:key, b.name_key) >= :match";
        String order = query == null
                ? " order by diaries desc, strains desc, b.name"
                : " order by (b.name_key like :prefix) desc, word_similarity(:key, b.name_key) desc, strains desc, b.name";
        Long total = jdbc.queryForObject("select count(*) from breeders b" + filter, params, Long.class);
        List<BreederListItem> items = jdbc.query(BREEDER_SELECT + filter + order + " limit :limit offset :offset",
                params, (rs, n) -> breederItem(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public PageResult<ProductListItem> searchProducts(String query, String category, PageQuery page) {
        MapSqlParameterSource params = paged(query, page).addValue("category", category);
        String filter = " where (cast(:category as varchar) is null or p.category = :category)" + (query == null ? ""
                : " and (p.name_key like :prefix or word_similarity(:key, p.name_key) >= :match or br.name_key like :prefix)");
        Long total = jdbc.queryForObject("select count(*) from products p join brands br on br.id = p.brand_id" + filter,
                params, Long.class);
        List<ProductListItem> items = jdbc.query(PRODUCT_SELECT + filter
                        + " order by diaries desc, br.name, p.name limit :limit offset :offset",
                params, (rs, n) -> productItem(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static final String PRODUCT_SELECT = """
            select p.id, p.name, br.id as brand_id, br.name as brand, p.category, p.status, p.source,
                   (select count(*) from diary_products dp join %s on d.id = dp.diary_id where dp.product_id = p.id) as diaries,
                   (select count(distinct ri.recipe_id) from recipe_ingredients ri
                      join recipes rc on rc.id = ri.recipe_id and rc.visibility = 'PUBLIC'
                      join users ru on ru.id = rc.owner_id and ru.status = 'ACTIVE'
                     where ri.product_id = p.id) as recipes,
                   pr.review_count, pr.rating
              from products p join brands br on br.id = p.brand_id
            """.formatted(PUBLIC_DIARIES) + PRODUCT_RATINGS;

    private static ProductListItem productItem(java.sql.ResultSet rs) throws SQLException {
        return new ProductListItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("brand_id", UUID.class), rs.getString("brand"), rs.getString("category"),
                rs.getString("status"), rs.getString("source"), rs.getLong("diaries"), rs.getLong("recipes"),
                rs.getLong("review_count"), rs.getBigDecimal("rating"));
    }

    // ---------------------------------------------------------------------------------------- detalhes

    @Override
    public Optional<StrainDetail> strain(UUID id) {
        return jdbc.query("""
                select s.*, b.name as breeder_name, b.kind as breeder_kind, r.review_count, r.overall,
                       (select count(*) from %1$s where d.strain_id = s.id) as diaries,
                       (select round(sum(d.harvest_dry_grams)::numeric / nullif(sum(d.harvest_plants), 0), 1)
                          from %1$s where d.strain_id = s.id and d.harvest_dry_grams is not null) as avg_gpp,
                       case when s.status = 'TESTING' then %2$s end as testers,
                       case when s.status = 'TESTING' then %3$s end as tester_reviews
                  from strains s left join breeders b on b.id = s.breeder_id
                """.formatted(PUBLIC_DIARIES, TestingProgressSql.TESTERS, TestingProgressSql.REVIEWS)
                        + STRAIN_REVIEWS + " where s.id = :id",
                new MapSqlParameterSource("id", id),
                (rs, n) -> new StrainDetail(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getObject("breeder_id", UUID.class), rs.getString("breeder_name"), rs.getString("flowering_type"),
                        rs.getString("dominance"), rs.getString("flowering_speed"), strings(rs.getArray("seed_types")),
                        strings(rs.getArray("flavors")), strings(rs.getArray("effects")), rs.getString("genetics"),
                        rs.getString("status"), rs.getString("source"), rs.getLong("diaries"), rs.getLong("review_count"),
                        rs.getBigDecimal("overall"), rs.getBigDecimal("avg_gpp"), instant(rs.getTimestamp("promoted_at")),
                        progress(rs), rs.getString("generation"), rs.getString("breeder_kind")))
                .stream().findFirst();
    }

    @Override
    public Optional<ProductListItem> product(UUID id) {
        return jdbc.query(PRODUCT_SELECT + " where p.id = :id", new MapSqlParameterSource("id", id),
                (rs, n) -> productItem(rs)).stream().findFirst();
    }

    @Override
    public PageResult<ProductReview> productReviews(UUID productId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("productId", productId)
                .addValue("limit", page.size()).addValue("offset", page.offset());
        // Sem autor: a nota de um produto em cultivo privado não aponta para ninguém.
        String from = """
                  from review_products rp
                  join reviews rv on rv.id = rp.review_id and not rv.hidden
                  join users ru on ru.id = rv.author_id and ru.status = 'ACTIVE'
                  join strains s on s.id = rv.strain_id
                  left join breeders b on b.id = s.breeder_id
                  join grow_diaries d on d.id = rv.diary_id
                 where rp.product_id = :productId
                """;
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        List<ProductReview> items = jdbc.query("""
                select rv.id, s.id as strain_id, s.name as strain, b.name as breeder, rp.rating,
                       case when d.visibility = 'PUBLIC' and not d.moderation_hold then d.id end as public_diary_id,
                       rv.created_at
                """ + from + " order by rv.created_at desc, rv.id limit :limit offset :offset",
                params,
                (rs, n) -> new ProductReview(rs.getObject("id", UUID.class), rs.getObject("strain_id", UUID.class),
                        rs.getString("strain"), rs.getString("breeder"), rs.getInt("rating"),
                        rs.getObject("public_diary_id", UUID.class), rs.getTimestamp("created_at").toInstant()));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    // ------------------------------------------------------------------------------ programa de breeders

    private static final String TESTING_SELECT = """
            select s.id, s.name, b.id as breeder_id, b.name as breeder_name, s.flowering_type, s.dominance, s.genetics,
                   s.generation, s.status, s.created_at, s.promoted_at,
                   case when s.status = 'TESTING' then %s end as testers,
                   case when s.status = 'TESTING' then %s end as tester_reviews
              from strains s join breeders b on b.id = s.breeder_id
            """.formatted(TestingProgressSql.TESTERS, TestingProgressSql.REVIEWS);

    @Override
    public PageResult<TestingStrainItem> testingStrains(String query, UUID breederId, PageQuery page) {
        MapSqlParameterSource params = paged(query, page).addValue("breederId", breederId, Types.OTHER);
        String filter = " where s.status = 'TESTING' and (cast(:breederId as uuid) is null or s.breeder_id = :breederId)"
                + (query == null ? ""
                : " and (s.name_key like :prefix or word_similarity(:key, s.name_key) >= :match or b.name_key like :prefix)");
        Long total = jdbc.queryForObject("select count(*) from strains s join breeders b on b.id = s.breeder_id" + filter,
                params, Long.class);
        List<TestingStrainItem> items = jdbc.query(TESTING_SELECT + filter
                        + " order by s.created_at desc, s.id limit :limit offset :offset",
                params, (rs, n) -> testingItem(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public List<Representation> representationsOf(UUID userId) {
        return jdbc.query("""
                select r.breeder_id, b.name, r.status, r.requested_at, r.decided_at, r.claimed_kind, r.public_credit
                  from breeder_representatives r join breeders b on b.id = r.breeder_id
                 where r.user_id = :userId
                 order by (r.status = 'APPROVED') desc, b.name""",
                new MapSqlParameterSource("userId", userId),
                (rs, n) -> new Representation(rs.getObject("breeder_id", UUID.class), rs.getString("name"),
                        rs.getString("status"), rs.getTimestamp("requested_at").toInstant(),
                        instant(rs.getTimestamp("decided_at")), rs.getString("claimed_kind"),
                        rs.getBoolean("public_credit")));
    }

    @Override
    public List<TestingStrainItem> breederStrainsOf(UUID userId) {
        return jdbc.query(TESTING_SELECT + """
                 where s.source = 'BREEDER'
                   and exists (select 1 from breeder_representatives r
                                where r.breeder_id = s.breeder_id and r.user_id = :userId and r.status = 'APPROVED')
                 order by (s.status = 'TESTING') desc, s.created_at desc
                 limit 200""",
                new MapSqlParameterSource("userId", userId), (rs, n) -> testingItem(rs));
    }

    @Override
    public PageResult<RepresentativeRequest> representativeRequests(PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from breeder_representatives", params, Long.class);
        List<RepresentativeRequest> items = jdbc.query("""
                select r.breeder_id, b.name as breeder, r.user_id, u.username, r.status, r.note, r.requested_at,
                       r.decided_at, r.claimed_kind, r.public_credit
                  from breeder_representatives r
                  join breeders b on b.id = r.breeder_id
                  join users u on u.id = r.user_id
                 order by (r.status = 'PENDING') desc, r.requested_at desc, r.breeder_id, r.user_id
                 limit :limit offset :offset""",
                params,
                (rs, n) -> new RepresentativeRequest(rs.getObject("breeder_id", UUID.class), rs.getString("breeder"),
                        rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getString("status"),
                        rs.getString("note"), rs.getTimestamp("requested_at").toInstant(),
                        instant(rs.getTimestamp("decided_at")), rs.getString("claimed_kind"),
                        rs.getBoolean("public_credit")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public long pendingRepresentatives() {
        Long count = jdbc.queryForObject("select count(*) from breeder_representatives where status = 'PENDING'",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    private static TestingStrainItem testingItem(java.sql.ResultSet rs) throws SQLException {
        return new TestingStrainItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("breeder_id", UUID.class), rs.getString("breeder_name"), rs.getString("flowering_type"),
                rs.getString("dominance"), rs.getString("genetics"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("promoted_at")), progress(rs),
                rs.getString("generation"));
    }

    /** Andamento só faz sentido enquanto a semente está em teste (colunas nulas nos demais casos). */
    private static Progress progress(java.sql.ResultSet rs) throws SQLException {
        long testers = rs.getLong("testers");
        if (rs.wasNull()) {
            return null;
        }
        return new Progress(testers, TestingProgress.TESTERS_REQUIRED, rs.getLong("tester_reviews"),
                TestingProgress.REVIEWS_REQUIRED);
    }

    // ------------------------------------------------------------------------------------ banco aberto

    @Override
    public List<OpenStrain> openStrains() {
        // Agregados em CTEs (uma passada por tabela) em vez de subconsultas por strain: o catálogo inteiro de uma vez.
        return jdbc.query("""
                with usage as (
                    select d.strain_id, count(*) as public_diaries, count(distinct d.owner_id) as growers,
                           round(sum(d.harvest_dry_grams)::numeric
                                 / nullif(sum(d.harvest_plants) filter (where d.harvest_dry_grams is not null), 0), 1)
                               as avg_gpp
                      from %s
                     where d.strain_id is not null
                     group by d.strain_id),
                ratings as (
                    select rv.strain_id, count(*) as review_count,
                           round(avg(rv.vigor), 1) as vigor,
                           round(avg(rv.pest_resistance), 1) as pest_resistance,
                           round(avg(rv.ease_of_growth), 1) as ease_of_growth,
                           round(avg(rv.yield_rating), 1) as yield_rating,
                           round(avg(rv.terpene_profile), 1) as terpene_profile,
                           round(avg((rv.vigor + rv.pest_resistance + rv.ease_of_growth + rv.yield_rating
                                      + rv.terpene_profile) / 5.0), 1) as overall
                      from reviews rv join users ru on ru.id = rv.author_id and ru.status = 'ACTIVE' and not rv.hidden
                     group by rv.strain_id)
                select s.id, s.name, b.name as breeder_name, b.kind as breeder_kind, s.flowering_type, s.dominance,
                       s.flowering_speed, s.seed_types, s.flavors, s.effects, s.genetics, s.generation, s.status,
                       s.source, s.promoted_at,
                       coalesce(u.public_diaries, 0) as public_diaries, coalesce(u.growers, 0) as growers, u.avg_gpp,
                       coalesce(r.review_count, 0) as review_count, r.vigor, r.pest_resistance, r.ease_of_growth,
                       r.yield_rating, r.terpene_profile, r.overall
                  from strains s
                  left join breeders b on b.id = s.breeder_id
                  left join usage u on u.strain_id = s.id
                  left join ratings r on r.strain_id = s.id
                 where %s
                 order by s.name_key, s.breeder_key""".formatted(PUBLIC_DIARIES, OFFICIAL),
                new MapSqlParameterSource(),
                (rs, n) -> new OpenStrain(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("breeder_name"), rs.getString("flowering_type"), rs.getString("dominance"),
                        rs.getString("flowering_speed"), strings(rs.getArray("seed_types")),
                        strings(rs.getArray("flavors")), strings(rs.getArray("effects")), rs.getString("genetics"),
                        rs.getString("status"), rs.getString("source"), instant(rs.getTimestamp("promoted_at")),
                        rs.getLong("public_diaries"), rs.getLong("growers"), rs.getLong("review_count"),
                        rs.getBigDecimal("vigor"), rs.getBigDecimal("pest_resistance"),
                        rs.getBigDecimal("ease_of_growth"), rs.getBigDecimal("yield_rating"),
                        rs.getBigDecimal("terpene_profile"), rs.getBigDecimal("overall"), rs.getBigDecimal("avg_gpp"),
                        rs.getString("breeder_kind"), rs.getString("generation")));
    }


    @Override
    public Optional<BreederListItem> breeder(UUID id) {
        return jdbc.query(BREEDER_SELECT + " where b.id = :id", new MapSqlParameterSource("id", id), (rs, n) -> breederItem(rs))
                .stream().findFirst();
    }

    @Override
    public PageResult<BreederStrainCard> strainsOfBreeder(UUID breederId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("breederId", breederId)
                .addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from strains s where s.breeder_id = :breederId and " + OFFICIAL,
                params, Long.class);
        // A capa só vem de diários públicos (PUBLIC_DIARIES), então foto de cultivo privado nunca vaza aqui.
        List<BreederStrainCard> items = jdbc.query("""
                select s.id, s.name, s.flowering_type, s.dominance, s.flowering_speed, s.genetics, s.status, s.source,
                       r.review_count, r.overall,
                       (select count(*) from %1$s where d.strain_id = s.id) as diaries,
                       (select round(sum(d.harvest_dry_grams)::numeric / nullif(sum(d.harvest_plants), 0), 1)
                          from %1$s where d.strain_id = s.id and d.harvest_dry_grams is not null) as avg_gpp,
                       cover.diary_id as cover_diary_id, cover.image_id as cover_photo_id
                  from strains s
                """.formatted(PUBLIC_DIARIES) + STRAIN_REVIEWS + """
                  left join lateral (
                      select d.id as diary_id, p.image_id
                        from %s
                        join diary_updates upd on upd.diary_id = d.id
                        join diary_update_photos p on p.update_id = upd.id
                       where d.strain_id = s.id
                       order by upd.recorded_on desc, upd.created_at desc, p.position
                       limit 1) cover on true
                 where s.breeder_id = :breederId and %s
                 order by diaries desc, r.review_count desc nulls last, s.name
                 limit :limit offset :offset""".formatted(PUBLIC_DIARIES, OFFICIAL),
                params,
                (rs, n) -> new BreederStrainCard(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("flowering_type"), rs.getString("dominance"), rs.getString("flowering_speed"),
                        rs.getString("genetics"), rs.getString("status"), rs.getString("source"), rs.getLong("diaries"),
                        rs.getLong("review_count"), rs.getBigDecimal("overall"), rs.getBigDecimal("avg_gpp"),
                        thumbnailUrl(rs.getObject("cover_diary_id", UUID.class), rs.getObject("cover_photo_id", UUID.class))));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    /** Mesmo endpoint de foto do diário: é a visibilidade do diário que autoriza a imagem. */
    private static String thumbnailUrl(UUID diaryId, UUID photoId) {
        return diaryId == null || photoId == null ? null
                : "/api/v1/diaries/" + diaryId + "/photos/" + photoId + "?size=THUMBNAIL";
    }

    // ---------------------------------------------------------------------------------------- rankings

    /** Única parte dinâmica: escolhida por enum, nunca por texto do usuário. */
    private static final Map<RankingKind, String> RANKING_SOURCE = Map.of(
            RankingKind.STRAINS, """
                    select s.id, s.name, b.name as parent_name, null as category, d.id as diary_id, d.owner_id,
                           d.harvest_dry_grams, d.harvest_plants
                      from strains s left join breeders b on b.id = s.breeder_id
                      join %s on d.strain_id = s.id
                     where s.status <> 'TESTING'""",
            RankingKind.BREEDERS, """
                    select b.id, b.name, null as parent_name, null as category, d.id as diary_id, d.owner_id,
                           d.harvest_dry_grams, d.harvest_plants
                      from breeders b join %s on d.breeder_id = b.id""",
            RankingKind.PRODUCTS, """
                    select p.id, p.name, br.name as parent_name, p.category, d.id as diary_id, d.owner_id,
                           d.harvest_dry_grams, d.harvest_plants
                      from products p join brands br on br.id = p.brand_id
                      join diary_products dp on dp.product_id = p.id
                      join %s on d.id = dp.diary_id
                     where (cast(:category as varchar) is null or p.category = :category)""",
            RankingKind.BRANDS, """
                    select br.id, br.name, null as parent_name, null as category, d.id as diary_id, d.owner_id,
                           d.harvest_dry_grams, d.harvest_plants
                      from brands br join products p on p.brand_id = br.id
                      join diary_products dp on dp.product_id = p.id
                      join %s on d.id = dp.diary_id
                     where (cast(:category as varchar) is null or p.category = :category)""");

    @Override
    public PageResult<RankingRow> ranking(RankingKind kind, String category, PageQuery page) {
        String usage = """
                select id, name, parent_name, max(category) as category,
                       count(distinct diary_id) as diaries, count(distinct owner_id) as growers,
                       round(sum(harvest_dry_grams)::numeric / nullif(sum(harvest_plants), 0), 1) as avg_gpp
                  from (%s) usage
                 group by id, name, parent_name""".formatted(RANKING_SOURCE.get(kind).formatted(PUBLIC_DIARIES));
        MapSqlParameterSource params = new MapSqlParameterSource("category", category)
                .addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from (" + usage + ") ranked", params, Long.class);
        List<RankingRow> items = jdbc.query("""
                select ranked.*, rank() over (order by diaries desc, growers desc) as position
                  from (%s) ranked
                 order by position, name limit :limit offset :offset""".formatted(usage),
                params,
                (rs, n) -> new RankingRow(rs.getLong("position"), rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("parent_name"), rs.getString("category"), rs.getLong("diaries"),
                        rs.getLong("growers"), rs.getBigDecimal("avg_gpp")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    // ------------------------------------------------------------------------------------- moderação

    /**
     * Por tipo: a tabela com alias {@code i} (com as colunas de {@link Suggestion} e {@code created_at}) e a
     * {@code name_key} usada na comparação de semelhança. Escolhido por enum, nunca por texto do usuário.
     */
    private static final Map<RankingKind, String> QUEUE_SOURCE = Map.of(
            RankingKind.STRAINS, """
                    select s.id, s.name, s.name_key, b.id as parent_id, b.name as parent_name, null as category,
                           s.status, s.source, s.created_at,
                           (select count(*) from grow_diaries d where d.strain_id = s.id) as usage
                      from strains s left join breeders b on b.id = s.breeder_id""",
            RankingKind.BREEDERS, """
                    select b.id, b.name, b.name_key, null as parent_id, null as parent_name, null as category,
                           b.status, b.source, b.created_at,
                           (select count(*) from grow_diaries d where d.breeder_id = b.id) as usage
                      from breeders b""",
            RankingKind.BRANDS, """
                    select br.id, br.name, br.name_key, null as parent_id, null as parent_name, null as category,
                           br.status, br.source, br.created_at,
                           (select count(*) from products p where p.brand_id = br.id) as usage
                      from brands br""",
            RankingKind.PRODUCTS, """
                    select p.id, p.name, p.name_key, br.id as parent_id, br.name as parent_name, p.category,
                           p.status, p.source, p.created_at,
                           (select count(*) from diary_products dp where dp.product_id = p.id) as usage
                      from products p join brands br on br.id = p.brand_id""");

    private static final String PENDING = " where i.status = 'UNVERIFIED' and i.source = 'COMMUNITY'";
    private static final double DUPLICATE_SIMILARITY = 0.4;
    private static final int DUPLICATE_CANDIDATES = 3;

    @Override
    public PageResult<PendingItem> pending(RankingKind kind, PageQuery page) {
        String source = "(" + QUEUE_SOURCE.get(kind) + ") i";
        MapSqlParameterSource params = new MapSqlParameterSource("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from " + source + PENDING, params, Long.class);
        List<PendingItem> items = jdbc.query("select i.* from " + source + PENDING
                        + " order by i.created_at desc, i.id limit :limit offset :offset",
                params,
                (rs, n) -> new PendingItem(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getObject("parent_id", UUID.class), rs.getString("parent_name"), rs.getString("category"),
                        rs.getTimestamp("created_at").toInstant(), rs.getLong("usage"), List.of()));
        return PageResult.of(items.stream().map(item -> withSimilar(source, item)).toList(), page,
                total == null ? 0 : total);
    }

    private PendingItem withSimilar(String source, PendingItem item) {
        List<Suggestion> similar = jdbc.query("""
                select i.* from %s, (select name_key from %s where i.id = :id) target
                 where i.id <> :id and similarity(i.name_key, target.name_key) >= :min
                 order by similarity(i.name_key, target.name_key) desc, i.usage desc, i.name
                 limit :limit""".formatted(source, source),
                new MapSqlParameterSource("id", item.id()).addValue("min", DUPLICATE_SIMILARITY)
                        .addValue("limit", DUPLICATE_CANDIDATES),
                (rs, n) -> suggestion(rs));
        return new PendingItem(item.id(), item.name(), item.parentId(), item.parentName(), item.category(),
                item.createdAt(), item.usage(), similar);
    }

    @Override
    public Map<RankingKind, Long> pendingCounts() {
        Map<RankingKind, Long> counts = new EnumMap<>(RankingKind.class);
        for (RankingKind kind : RankingKind.values()) {
            Long count = jdbc.queryForObject("select count(*) from (" + QUEUE_SOURCE.get(kind) + ") i" + PENDING,
                    new MapSqlParameterSource(), Long.class);
            counts.put(kind, count == null ? 0 : count);
        }
        return counts;
    }

    // ----------------------------------------------------------------------------------------- helpers

    private static final String BREEDER_SELECT = """
            select b.id, b.name, b.status, b.source, b.kind,
                   (select count(*) from strains s where s.breeder_id = b.id and s.status <> 'TESTING') as strains,
                   (select array_agg(cu.username order by cu.username)
                      from breeder_representatives cr join users cu on cu.id = cr.user_id and cu.status = 'ACTIVE'
                     where cr.breeder_id = b.id and cr.status = 'APPROVED' and cr.public_credit
                       and cr.claimed_kind = 'INDEPENDENT') as creators,
                   (select count(*) from %s where d.breeder_id = b.id) as diaries,
                   r.review_count, r.overall
              from breeders b
              left join lateral (
                  select count(*) as review_count,
                         round(avg((rv.seedbank_germination + rv.seedbank_stability + rv.seedbank_service) / 3.0), 1) as overall
                    from reviews rv join users ru on ru.id = rv.author_id and ru.status = 'ACTIVE' and not rv.hidden
                   where rv.seedbank_id = b.id) r on true
            """.formatted(PUBLIC_DIARIES);

    private static BreederListItem breederItem(java.sql.ResultSet rs) throws SQLException {
        return new BreederListItem(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                rs.getString("source"), rs.getLong("strains"), rs.getLong("diaries"), rs.getLong("review_count"),
                rs.getBigDecimal("overall"), rs.getString("kind"), strings(rs.getArray("creators")));
    }

    private static Suggestion suggestion(java.sql.ResultSet rs) throws SQLException {
        return new Suggestion(rs.getObject("id", UUID.class), rs.getString("name"), rs.getObject("parent_id", UUID.class),
                rs.getString("parent_name"), rs.getString("category"), rs.getString("status"), rs.getString("source"),
                rs.getLong("usage"));
    }

    /** O texto digitado é normalizado igual às chaves, e curingas do LIKE ficam impossíveis (a chave só tem [a-z0-9 ]). */
    private static MapSqlParameterSource search(String query) {
        String key = CatalogKey.of(query);
        return new MapSqlParameterSource("key", key).addValue("prefix", key + "%").addValue("match", MATCH);
    }

    private static MapSqlParameterSource paged(String query, PageQuery page) {
        MapSqlParameterSource params = query == null ? new MapSqlParameterSource() : search(query);
        return params.addValue("limit", page.size()).addValue("offset", page.offset());
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
