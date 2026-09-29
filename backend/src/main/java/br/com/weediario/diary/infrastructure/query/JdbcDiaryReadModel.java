package br.com.weediario.diary.infrastructure.query;

import br.com.weediario.diary.application.query.DiaryReadModel;
import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
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
 * É o lado de leitura, então pode juntar tabelas de contextos diferentes (o apelido do autor vem de {@code users}).
 */
@Component
public class JdbcDiaryReadModel implements DiaryReadModel {

    private static final String SELECT = """
            select d.id, d.title, u.username, d.strain_name, d.breeder, d.flowering_type, d.seed_sex, d.medium,
                   d.status, d.visibility, d.current_stage, d.started_on, d.ended_on, d.last_update_on,
                   (select count(*) from diary_updates du where du.diary_id = d.id) as updates_count,
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
            FeedSort.NEWEST, " order by d.created_at desc, d.id");

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDiaryReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<DiarySummary> publicFeed(FeedFilter filter, PageQuery page) {
        List<String> conditions = new ArrayList<>(List.of("d.visibility = 'PUBLIC'", "u.status = 'ACTIVE'"));
        MapSqlParameterSource params = new MapSqlParameterSource();
        addEquals(conditions, params, "d.current_stage", "stage", filter.stage());
        addEquals(conditions, params, "d.medium", "medium", filter.medium());
        addEquals(conditions, params, "d.status", "status", filter.status());
        addEquals(conditions, params, "d.flowering_type", "floweringType", filter.floweringType());
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
        String sql = SELECT + """
                 where d.id = :id
                   and ((d.visibility = 'PUBLIC' and u.status = 'ACTIVE') or d.owner_id = :viewerId)""";
        return jdbc.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    private PageResult<DiarySummary> page(String where, String orderBy, MapSqlParameterSource params, PageQuery page) {
        Long total = jdbc.queryForObject(COUNT + where, params, Long.class);
        params.addValue("limit", page.size()).addValue("offset", page.offset());
        List<DiarySummary> items = jdbc.query(SELECT + where + orderBy + " limit :limit offset :offset", params, ROW_MAPPER);
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

    private static final RowMapper<DiarySummary> ROW_MAPPER = (rs, rowNum) -> new DiarySummary(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("username"),
            rs.getString("strain_name"),
            rs.getString("breeder"),
            FloweringType.valueOf(rs.getString("flowering_type")),
            SeedSex.valueOf(rs.getString("seed_sex")),
            GrowMedium.valueOf(rs.getString("medium")),
            DiaryStatus.valueOf(rs.getString("status")),
            Visibility.valueOf(rs.getString("visibility")),
            GrowthStage.valueOf(rs.getString("current_stage")),
            toLocalDate(rs.getDate("started_on")),
            toLocalDate(rs.getDate("ended_on")),
            toLocalDate(rs.getDate("last_update_on")),
            rs.getLong("updates_count"),
            rs.getObject("cover_photo_id", UUID.class));

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
