package br.com.weediario.diary.infrastructure.query;

import br.com.weediario.diary.application.query.DiaryReadModel;
import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.Harvest;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.SetupSnapshot;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas do feed em SQL explícito. Todos os valores vêm por parâmetro nomeado (sem concatenação de entrada
 * do usuário); os únicos trechos dinâmicos são cláusulas fixas escolhidas por enum.
 * <p>
 * É o lado de leitura, então pode juntar tabelas de contextos diferentes (o apelido do autor vem de {@code users},
 * as contagens sociais de {@code diary_comments}/{@code diary_reactions}).
 */
@Component
public class JdbcDiaryReadModel implements DiaryReadModel {

    private static final String SELECT = """
            select d.id, d.title, u.username, d.strain_name, d.breeder, d.strain_id, d.flowering_type, d.seed_sex, d.medium,
                   d.status, d.visibility, d.current_stage, d.started_on, d.ended_on, d.last_update_on,
                   d.harvest_dry_grams, d.harvest_plants,
                   (select count(*) from diary_updates du where du.diary_id = d.id) as updates_count,
                   (select count(*) from diary_comments dc where dc.diary_id = d.id and not dc.hidden) as comments_count,
                   (select count(*) from diary_reactions dr where dr.diary_id = d.id) as reactions_count,
                   %s as setup_snapshot,
                   (select p.image_id
                      from diary_update_photos p
                      join diary_updates du on du.id = p.update_id
                     where du.diary_id = d.id
                     order by du.recorded_on desc, du.created_at desc, p.position
                     limit 1) as cover_photo_id
              from grow_diaries d
              join users u on u.id = d.owner_id
            """;

    private static final String COUNT = """
            select count(*)
              from grow_diaries d
              join users u on u.id = d.owner_id
            """;

    private static final Map<FeedSort, String> ORDER_BY = Map.of(
            FeedSort.RECENTLY_UPDATED, " order by coalesce(d.last_update_on, d.started_on) desc, d.updated_at desc, d.id",
            FeedSort.NEWEST, " order by d.created_at desc, d.id",
            FeedSort.MOST_REACTED, " order by reactions_count desc, coalesce(d.last_update_on, d.started_on) desc, d.id");

    /** Listagens não carregam o setup (JSON maior e irrelevante para o card); o detalhe carrega. */
    private static final String SELECT_LIST = SELECT.formatted("null::jsonb");
    private static final String SELECT_DETAIL = SELECT.formatted("d.setup_snapshot");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<DiarySummary> rowMapper;

    public JdbcDiaryReadModel(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.rowMapper = this::mapRow;
    }

    @Override
    public PageResult<DiarySummary> publicFeed(FeedFilter filter, Optional<UUID> viewerId, PageQuery page) {
        List<String> conditions = new ArrayList<>(List.of("d.visibility = 'PUBLIC'", "u.status = 'ACTIVE'"));
        MapSqlParameterSource params = new MapSqlParameterSource();
        addEquals(conditions, params, "d.current_stage", "stage", filter.stage());
        addEquals(conditions, params, "d.medium", "medium", filter.medium());
        addEquals(conditions, params, "d.status", "status", filter.status());
        addEquals(conditions, params, "d.flowering_type", "floweringType", filter.floweringType());
        if (filter.following()) {
            conditions.add("d.owner_id in (select f.followee_id from grower_follows f where f.follower_id = :viewerId)");
            params.addValue("viewerId", viewerId.orElseThrow());
        }
        if (filter.author() != null) {
            conditions.add("lower(u.username) = lower(:author)");
            params.addValue("author", filter.author());
        }
        // Por id do catálogo: pega o cultivo mesmo que o nome tenha sido digitado com outra grafia.
        if (filter.strainId() != null) {
            conditions.add("d.strain_id = :strainId");
            params.addValue("strainId", filter.strainId());
        }
        if (filter.breederId() != null) {
            conditions.add("d.breeder_id = :breederId");
            params.addValue("breederId", filter.breederId());
        }
        if (filter.strain() != null) {
            conditions.add("d.strain_name ilike :strain escape '\\'");
            params.addValue("strain", "%" + escapeLike(filter.strain()) + "%");
        }
        return page(" where " + String.join(" and ", conditions), ORDER_BY.get(filter.sort()), params, page);
    }

    @Override
    public PageResult<DiarySummary> ownedBy(UUID ownerId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("ownerId", ownerId);
        return page(" where d.owner_id = :ownerId", ORDER_BY.get(FeedSort.RECENTLY_UPDATED), params, page);
    }

    @Override
    public Optional<DiarySummary> findVisible(UUID diaryId, Optional<UUID> viewerId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", diaryId)
                .addValue("viewerId", viewerId.orElse(null), java.sql.Types.OTHER);
        String sql = SELECT_DETAIL + """
                 where d.id = :id
                   and ((d.visibility = 'PUBLIC' and u.status = 'ACTIVE') or d.owner_id = :viewerId)""";
        return jdbc.query(sql, params, rowMapper).stream().findFirst();
    }

    private PageResult<DiarySummary> page(String where, String orderBy, MapSqlParameterSource params, PageQuery page) {
        Long total = jdbc.queryForObject(COUNT + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<DiarySummary> items = jdbc.query(SELECT_LIST + where + orderBy + " limit :limit offset :offset", params,
                rowMapper);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static void addEquals(List<String> conditions, MapSqlParameterSource params,
                                  String column, String name, Enum<?> value) {
        if (value != null) {
            conditions.add(column + " = :" + name);
            params.addValue(name, value.name());
        }
    }

    /** Impede que %, _ e \ digitados pelo usuário virem curingas do LIKE. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private DiarySummary mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DiarySummary(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("username"),
            rs.getString("strain_name"),
            rs.getString("breeder"),
            rs.getObject("strain_id", UUID.class),
            FloweringType.valueOf(rs.getString("flowering_type")),
            SeedSex.valueOf(rs.getString("seed_sex")),
            GrowMedium.valueOf(rs.getString("medium")),
            DiaryStatus.valueOf(rs.getString("status")),
            Visibility.valueOf(rs.getString("visibility")),
            GrowthStage.valueOf(rs.getString("current_stage")),
            toLocalDate(rs.getDate("started_on")),
            toLocalDate(rs.getDate("ended_on")),
            toLocalDate(rs.getDate("last_update_on")),
            harvestOf(rs.getObject("harvest_dry_grams", Integer.class), rs.getObject("harvest_plants", Integer.class)),
            rs.getLong("updates_count"),
            rs.getLong("comments_count"),
            rs.getLong("reactions_count"),
            rs.getObject("cover_photo_id", UUID.class),
            parseSetup(rs.getString("setup_snapshot")));
    }

    @Override
    public List<PublicSetup> publicSetupsOf(String username, int limit) {
        return jdbc.query("""
                select d.setup_snapshot::text as setup_snapshot, count(*) as diaries, max(d.started_on) as last_started_on
                  from grow_diaries d
                  join users u on u.id = d.owner_id and u.status = 'ACTIVE'
                 where lower(u.username) = lower(:username) and d.visibility = 'PUBLIC'
                   and d.setup_snapshot ->> 'name' is not null
                 group by d.setup_snapshot
                 order by last_started_on desc, diaries desc
                 limit :limit""",
                new MapSqlParameterSource("username", username).addValue("limit", limit),
                (rs, rowNum) -> new PublicSetup(parseSetup(rs.getString("setup_snapshot")), rs.getLong("diaries"),
                        toLocalDate(rs.getDate("last_started_on"))));
    }

    private static Harvest harvestOf(Integer dryGrams, Integer plants) {
        return dryGrams == null || plants == null ? null : new Harvest(dryGrams, plants);
    }

    private SetupSnapshot parseSetup(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return json.readValue(raw, SetupSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Setup do diário corrompido", e);
        }
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
