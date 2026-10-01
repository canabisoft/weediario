package br.com.weediario.review.infrastructure.query;

import br.com.weediario.review.application.query.StrainCatalog;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregações do catálogo em SQL parametrizado. Reviews de contas suspensas não entram nas médias.
 */
@Component
public class JdbcStrainCatalog implements StrainCatalog {

    private static final String SUMMARY = """
            select s.id, s.name, s.breeder,
                   count(r.id) as review_count,
                   round(avg((r.vigor + r.pest_resistance + r.ease_of_growth + r.yield_rating + r.terpene_profile) / 5.0), 1) as overall,
                   round(avg(r.vigor), 1) as vigor,
                   round(avg(r.pest_resistance), 1) as pest_resistance,
                   round(avg(r.ease_of_growth), 1) as ease_of_growth,
                   round(avg(r.yield_rating), 1) as yield_rating,
                   round(avg(r.terpene_profile), 1) as terpene_profile
            """;
    private static final String REVIEWED = """
              from strains s
              join reviews r on r.strain_id = s.id
              join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
            """;
    /** Qualquer strain do catálogo, mesmo sem reviews (contagem zero e médias nulas). */
    private static final String ANY = """
              from strains s
              left join (reviews r join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden) on r.strain_id = s.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStrainCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<StrainSummary> search(String nameQuery, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        // Sementes em teste ficam fora da busca de genéticas avaliadas até entrarem no banco oficial.
        String where = " where s.status <> 'TESTING'";
        if (nameQuery != null) {
            where += " and s.name ilike :name escape '\\'";
            params.addValue("name", "%" + escapeLike(nameQuery) + "%");
        }
        Long total = jdbc.queryForObject("""
                select count(distinct s.id)
                  from strains s
                  join reviews r on r.strain_id = s.id
                  join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
                """ + where, params, Long.class);
        List<StrainSummary> items = jdbc.query(SUMMARY + REVIEWED + where
                + " group by s.id order by review_count desc, overall desc, s.name limit :limit offset :offset",
                params, SUMMARY_MAPPER);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Optional<StrainSummary> find(UUID strainId) {
        return jdbc.query(SUMMARY + ANY + " where s.id = :id group by s.id", new MapSqlParameterSource("id", strainId),
                SUMMARY_MAPPER).stream().findFirst();
    }

    @Override
    public PageResult<ReviewView> reviewsOf(UUID strainId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("strainId", strainId)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String from = """
                  from reviews r
                  join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
                  join grow_diaries d on d.id = r.diary_id
                 where r.strain_id = :strainId
                """;
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        List<ReviewView> items = jdbc.query("""
                select r.id, u.username, case when d.visibility = 'PUBLIC' then d.id end as public_diary_id,
                       r.vigor, r.pest_resistance, r.ease_of_growth, r.yield_rating, r.terpene_profile,
                       r.report, r.created_at
                """ + from + " order by r.created_at desc, r.id limit :limit offset :offset", params, REVIEW_MAPPER);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public PageResult<AuthoredReview> reviewsBy(String username, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("username", username)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String from = """
                  from reviews r
                  join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
                  join strains s on s.id = r.strain_id
                  join grow_diaries d on d.id = r.diary_id
                 where lower(u.username) = lower(:username)
                """;
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        List<AuthoredReview> items = jdbc.query("""
                select r.id, s.id as strain_id, s.name as strain, s.breeder,
                       case when d.visibility = 'PUBLIC' then d.id end as public_diary_id,
                       round((r.vigor + r.pest_resistance + r.ease_of_growth + r.yield_rating + r.terpene_profile) / 5.0, 1)
                           as overall,
                       r.report, r.created_at
                """ + from + " order by r.created_at desc, r.id limit :limit offset :offset",
                params,
                (rs, rowNum) -> new AuthoredReview(
                        rs.getObject("id", UUID.class),
                        rs.getObject("strain_id", UUID.class),
                        rs.getString("strain"),
                        rs.getString("breeder"),
                        rs.getObject("public_diary_id", UUID.class),
                        rs.getBigDecimal("overall"),
                        rs.getString("report"),
                        rs.getTimestamp("created_at").toInstant()));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<StrainSummary> SUMMARY_MAPPER = (rs, rowNum) -> new StrainSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("breeder"),
            rs.getLong("review_count"),
            rs.getBigDecimal("overall"),
            rs.getBigDecimal("vigor"),
            rs.getBigDecimal("pest_resistance"),
            rs.getBigDecimal("ease_of_growth"),
            rs.getBigDecimal("yield_rating"),
            rs.getBigDecimal("terpene_profile"));

    private static final RowMapper<ReviewView> REVIEW_MAPPER = (rs, rowNum) -> new ReviewView(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getObject("public_diary_id", UUID.class),
            rs.getInt("vigor"),
            rs.getInt("pest_resistance"),
            rs.getInt("ease_of_growth"),
            rs.getInt("yield_rating"),
            rs.getInt("terpene_profile"),
            rs.getString("report"),
            rs.getTimestamp("created_at").toInstant());
}
