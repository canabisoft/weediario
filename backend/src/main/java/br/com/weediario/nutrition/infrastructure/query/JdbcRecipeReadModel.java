package br.com.weediario.nutrition.infrastructure.query;

import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.domain.model.IngredientUnit;
import br.com.weediario.nutrition.domain.model.RecipeApplication;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.nutrition.domain.model.RecipeStage;
import br.com.weediario.nutrition.domain.model.RecipeVisibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcRecipeReadModel implements RecipeReadModel {

    private static final String ACTIVE_OWNER = "join users u on u.id = r.owner_id and u.status = 'ACTIVE'";

    /** Réplicas contam mesmo privadas (só o número aparece); cultivos, só os públicos de contas ativas. */
    private static final String SUMMARY = """
            select r.id, r.name, r.kind, r.application, r.stage, u.username as author, r.visibility, r.batch_liters,
                   r.updated_at,
                   array(select i.name from recipe_ingredients i where i.recipe_id = r.id order by i.position limit 4)
                       as ingredient_names,
                   (select count(*) from recipe_ingredients i where i.recipe_id = r.id) as ingredient_count,
                   (select count(*) from recipes c join users cu on cu.id = c.owner_id and cu.status = 'ACTIVE'
                     where c.replicated_from = r.id) as replications,
                   (select count(*) from diary_recipes dr
                      join grow_diaries d on d.id = dr.diary_id and d.visibility = 'PUBLIC'
                      join users du on du.id = d.owner_id and du.status = 'ACTIVE'
                     where dr.recipe_id = r.id) as diaries,
                   rt.rating_count, rt.rating, rt.rating_sum
              from recipes r
            """ + ACTIVE_OWNER + """
              left join lateral (
                  select count(*) as rating_count, round(avg(rr.stars), 1) as rating,
                         coalesce(sum(rr.stars), 0) as rating_sum
                    from recipe_ratings rr join users ru on ru.id = rr.rater_id and ru.status = 'ACTIVE'
                   where rr.recipe_id = r.id) rt on true
            """;

    /**
     * Média bayesiana: como se toda receita começasse com {@value #PRIOR_VOTES} notas {@value #PRIOR_STARS}.
     * Uma única nota 5 não passa na frente de uma receita com dezenas de notas 4,8.
     */
    private static final int PRIOR_VOTES = 3;
    private static final int PRIOR_STARS = 3;
    private static final String SCORE = "((%d * %d + rating_sum)::numeric / (%d + rating_count))"
            .formatted(PRIOR_VOTES, PRIOR_STARS, PRIOR_VOTES);

    /** Única parte dinâmica da ordenação: escolhida por enum, nunca por texto do usuário. */
    private static final Map<RecipeSort, String> ORDER_BY = Map.of(
            RecipeSort.RECENT, " order by r.updated_at desc, r.id",
            RecipeSort.MOST_REPLICATED, " order by replications desc, r.updated_at desc, r.id",
            RecipeSort.MOST_USED, " order by diaries desc, replications desc, r.updated_at desc, r.id",
            RecipeSort.TOP_RATED, " order by " + SCORE.replace("rating_sum", "rt.rating_sum").replace("rating_count", "rt.rating_count")
                    + " desc, rt.rating_count desc, diaries desc, r.updated_at desc, r.id");

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRecipeReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<RecipeSummary> publicRecipes(RecipeFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>(List.of("r.visibility = 'PUBLIC'"));
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter.kind() != null) {
            conditions.add("r.kind = :kind");
            params.addValue("kind", filter.kind().name());
        }
        if (filter.productId() != null) {
            conditions.add("exists (select 1 from recipe_ingredients i where i.recipe_id = r.id and i.product_id = :productId)");
            params.addValue("productId", filter.productId());
        }
        if (filter.query() != null) {
            conditions.add("""
                    (r.name ilike :like escape '\\' or exists (select 1 from recipe_ingredients i
                      where i.recipe_id = r.id and (i.name ilike :like escape '\\' or i.brand ilike :like escape '\\')))""");
            params.addValue("like", "%" + escapeLike(filter.query()) + "%");
        }
        return page(" where " + String.join(" and ", conditions), ORDER_BY.get(filter.sort()), params, page);
    }

    @Override
    public PageResult<RecipeSummary> ofOwner(UUID ownerId, PageQuery page) {
        return page(" where r.owner_id = :ownerId", ORDER_BY.get(RecipeSort.RECENT),
                new MapSqlParameterSource("ownerId", ownerId), page);
    }

    @Override
    public PageResult<RecipeSummary> publicOfGrower(String username, PageQuery page) {
        return page(" where r.visibility = 'PUBLIC' and lower(u.username) = lower(:username)",
                ORDER_BY.get(RecipeSort.MOST_USED), new MapSqlParameterSource("username", username), page);
    }

    @Override
    public Optional<RecipeView> detail(UUID recipeId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", recipeId);
        List<IngredientView> ingredients = jdbc.query(
                "select * from recipe_ingredients where recipe_id = :id order by position", params,
                (rs, n) -> new IngredientView(rs.getObject("product_id", UUID.class), rs.getString("name"),
                        rs.getString("brand"), rs.getBigDecimal("quantity"), IngredientUnit.valueOf(rs.getString("unit"))));
        return jdbc.query("""
                select s.*, r.batch_liters, r.target_ec, r.target_ph, r.notes, r.created_at,
                       o.id as origin_id, o.name as origin_name, ou.username as origin_author
                  from (%s where r.id = :id) s
                  join recipes r on r.id = s.id
                  left join recipes o on o.id = r.replicated_from and o.visibility = 'PUBLIC'
                  left join users ou on ou.id = o.owner_id and ou.status = 'ACTIVE'""".formatted(SUMMARY),
                params,
                (rs, n) -> new RecipeView(rs.getObject("id", UUID.class), rs.getString("name"),
                        RecipeKind.valueOf(rs.getString("kind")), RecipeApplication.valueOf(rs.getString("application")),
                        stage(rs), rs.getBigDecimal("batch_liters"), rs.getBigDecimal("target_ec"),
                        rs.getBigDecimal("target_ph"), rs.getString("notes"),
                        RecipeVisibility.valueOf(rs.getString("visibility")), rs.getString("author"), ingredients,
                        rs.getLong("replications"), rs.getLong("diaries"),
                        rs.getString("origin_author") == null ? null
                                : new RecipeOrigin(rs.getObject("origin_id", UUID.class), rs.getString("origin_name"),
                                        rs.getString("origin_author")),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                        rs.getLong("rating_count"), rs.getBigDecimal("rating")))
                .stream().findFirst();
    }

    @Override
    public List<RecipeSummary> summaries(Collection<UUID> recipeIds) {
        if (recipeIds.isEmpty()) {
            return List.of();
        }
        List<UUID> order = List.copyOf(recipeIds);
        return jdbc.query(SUMMARY + " where r.id in (:ids)", new MapSqlParameterSource("ids", order),
                        (rs, n) -> summary(rs)).stream()
                .sorted(Comparator.comparingInt(summary -> order.indexOf(summary.id())))
                .toList();
    }

    private static final String COMMENT_COLUMNS =
            "select c.id, c.author_id, u.username, c.body, c.hidden, c.created_at, c.parent_id";
    private static final String COMMENT_FROM = " from recipe_comments c join users u on u.id = c.author_id";

    /** Autor ativo; oculto só para a moderação ou para quem escreveu. */
    private static String visibleComment(String alias, boolean includeHidden) {
        String visibility = includeHidden ? "" : " and (not %1$s.hidden or %1$s.author_id = :viewerId)".formatted(alias);
        return " exists (select 1 from users au where au.id = %1$s.author_id and au.status = 'ACTIVE')".formatted(alias)
                + visibility;
    }

    @Override
    public PageResult<CommentRow> comments(UUID recipeId, boolean includeHidden, Optional<UUID> viewerId, PageQuery page) {
        String where = " where c.recipe_id = :recipeId and c.parent_id is null and" + visibleComment("c", includeHidden);
        MapSqlParameterSource params = new MapSqlParameterSource("recipeId", recipeId)
                .addValue("viewerId", viewerId.orElse(null), Types.OTHER)
                .addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*)" + COMMENT_FROM + where, params, Long.class);
        List<CommentRow> items = jdbc.query(COMMENT_COLUMNS + COMMENT_FROM + where
                + " order by c.created_at, c.id limit :limit offset :offset", params, (rs, n) -> commentRow(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Map<UUID, CommentReplies> replyPreviews(Collection<UUID> rootIds, boolean includeHidden,
                                                   Optional<UUID> viewerId, int perRoot) {
        if (rootIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("rootIds", List.copyOf(rootIds))
                .addValue("viewerId", viewerId.orElse(null), Types.OTHER)
                .addValue("perRoot", perRoot);
        Map<UUID, Long> totals = new HashMap<>();
        Map<UUID, List<CommentRow>> items = new LinkedHashMap<>();
        jdbc.query("select * from (" + COMMENT_COLUMNS
                        + ", row_number() over (partition by c.parent_id order by c.created_at, c.id) as position"
                        + ", count(*) over (partition by c.parent_id) as total"
                        + COMMENT_FROM + " where c.parent_id in (:rootIds) and" + visibleComment("c", includeHidden)
                        + ") x where x.position <= :perRoot order by x.created_at, x.id",
                params,
                rs -> {
                    UUID rootId = rs.getObject("parent_id", UUID.class);
                    totals.put(rootId, rs.getLong("total"));
                    items.computeIfAbsent(rootId, id -> new ArrayList<>()).add(commentRow(rs));
                });
        Map<UUID, CommentReplies> previews = new HashMap<>();
        items.forEach((rootId, replies) -> previews.put(rootId, new CommentReplies(List.copyOf(replies), totals.get(rootId))));
        return previews;
    }

    @Override
    public PageResult<CommentRow> replies(UUID rootId, boolean includeHidden, Optional<UUID> viewerId, PageQuery page) {
        String where = " where c.parent_id = :rootId and" + visibleComment("c", includeHidden)
                + " and exists (select 1 from recipe_comments p where p.id = c.parent_id and p.parent_id is null and"
                + visibleComment("p", includeHidden) + ")";
        MapSqlParameterSource params = new MapSqlParameterSource("rootId", rootId)
                .addValue("viewerId", viewerId.orElse(null), Types.OTHER)
                .addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*)" + COMMENT_FROM + where, params, Long.class);
        List<CommentRow> items = jdbc.query(COMMENT_COLUMNS + COMMENT_FROM + where
                + " order by c.created_at, c.id limit :limit offset :offset", params, (rs, n) -> commentRow(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Optional<CommentRow> comment(UUID commentId) {
        return jdbc.query(COMMENT_COLUMNS + COMMENT_FROM + " where c.id = :id", new MapSqlParameterSource("id", commentId),
                (rs, n) -> commentRow(rs)).stream().findFirst();
    }

    private static CommentRow commentRow(ResultSet rs) throws SQLException {
        return new CommentRow(rs.getObject("id", UUID.class), rs.getObject("author_id", UUID.class),
                rs.getString("username"), rs.getString("body"), rs.getBoolean("hidden"),
                rs.getTimestamp("created_at").toInstant(), rs.getObject("parent_id", UUID.class));
    }

    @Override
    public List<Recommendation> recommendations(Optional<UUID> viewerId, RecipeKind kind, UUID excludeDiaryId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("viewerId", viewerId.orElse(null), Types.OTHER)
                .addValue("kind", kind == null ? null : kind.name())
                .addValue("diaryId", excludeDiaryId, Types.OTHER)
                .addValue("limit", limit);
        // Produtos que o grower já usa: dos próprios cultivos e setups (sem viewer, o conjunto fica vazio).
        return jdbc.query("""
                with my_products as (
                    select dp.product_id from diary_products dp
                      join grow_diaries d on d.id = dp.diary_id where d.owner_id = cast(:viewerId as uuid)
                    union
                    select gp.product_id from grow_setup_products gp
                      join grow_setups gs on gs.id = gp.setup_id where gs.owner_id = cast(:viewerId as uuid))
                select s.*,
                       (select count(distinct i.product_id) from recipe_ingredients i
                         where i.recipe_id = s.id and i.product_id in (select product_id from my_products)) as shared_products
                  from (%s
                         where r.visibility = 'PUBLIC'
                           and (cast(:kind as varchar) is null or r.kind = :kind)
                           and (cast(:viewerId as uuid) is null or r.owner_id <> cast(:viewerId as uuid))
                           and not exists (select 1 from recipes mine
                                            where mine.owner_id = cast(:viewerId as uuid) and mine.replicated_from = r.id)
                           and not exists (select 1 from diary_recipes dr
                                            where dr.diary_id = cast(:diaryId as uuid) and dr.recipe_id = r.id)) s
                 order by shared_products desc, %s desc, s.diaries desc, s.replications desc, s.updated_at desc, s.id
                 limit :limit""".formatted(SUMMARY, SCORE.replace("rating_sum", "s.rating_sum").replace("rating_count", "s.rating_count")),
                params,
                (rs, n) -> new Recommendation(summary(rs), rs.getLong("shared_products")));
    }

    @Override
    public List<String> suggestIngredientNames(String query, int limit) {
        return jdbc.queryForList("""
                select min(i.name) from recipe_ingredients i
                  join recipes r on r.id = i.recipe_id and r.visibility = 'PUBLIC'
                 where i.product_id is null and unaccent(i.name) ilike unaccent(:like) escape '\\'
                 group by lower(unaccent(i.name))
                 order by count(*) desc, min(i.name)
                 limit :limit""",
                new MapSqlParameterSource("like", "%" + escapeLike(query) + "%").addValue("limit", limit), String.class);
    }

    private PageResult<RecipeSummary> page(String where, String orderBy, MapSqlParameterSource params, PageQuery page) {
        Long total = jdbc.queryForObject("select count(*) from recipes r " + ACTIVE_OWNER + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<RecipeSummary> items = jdbc.query(SUMMARY + where + orderBy + " limit :limit offset :offset", params,
                (rs, n) -> summary(rs));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static RecipeSummary summary(ResultSet rs) throws SQLException {
        return new RecipeSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                RecipeKind.valueOf(rs.getString("kind")), RecipeApplication.valueOf(rs.getString("application")),
                stage(rs), rs.getString("author"), RecipeVisibility.valueOf(rs.getString("visibility")),
                rs.getBigDecimal("batch_liters"), strings(rs.getArray("ingredient_names")),
                rs.getLong("ingredient_count"), rs.getLong("replications"), rs.getLong("diaries"),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("rating_count"), rs.getBigDecimal("rating"));
    }

    private static RecipeStage stage(ResultSet rs) throws SQLException {
        String stage = rs.getString("stage");
        return stage == null ? null : RecipeStage.valueOf(stage);
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    /** Impede que %, _ e \ digitados pelo usuário virem curingas do LIKE. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
