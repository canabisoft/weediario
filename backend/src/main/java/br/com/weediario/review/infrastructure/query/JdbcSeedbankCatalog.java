package br.com.weediario.review.infrastructure.query;

import br.com.weediario.review.application.query.SeedbankCatalog;
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
 * Médias por banco de sementes, só com avaliações de contas ativas.
 */
@Component
public class JdbcSeedbankCatalog implements SeedbankCatalog {

    private static final String SUMMARY = """
            select b.id, b.name,
                   count(r.id) as review_count,
                   round(avg((r.seedbank_germination + r.seedbank_stability + r.seedbank_service) / 3.0), 1) as overall,
                   round(avg(r.seedbank_germination), 1) as germination,
                   round(avg(r.seedbank_stability), 1) as stability,
                   round(avg(r.seedbank_service), 1) as service
            """;
    private static final String REVIEWED = """
              from breeders b
              join reviews r on r.seedbank_id = b.id
              join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
            """;
    /** Qualquer breeder do catálogo, mesmo sem reviews (contagem zero e médias nulas). */
    private static final String ANY = """
              from breeders b
              left join (reviews r join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden) on r.seedbank_id = b.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSeedbankCatalog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<SeedbankSummary> search(String nameQuery, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String where = "";
        if (nameQuery != null) {
            where = " where b.name ilike :name escape '\\'";
            params.addValue("name", "%" + escapeLike(nameQuery) + "%");
        }
        Long total = jdbc.queryForObject("""
                select count(distinct b.id)
                  from breeders b
                  join reviews r on r.seedbank_id = b.id
                  join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
                """ + where, params, Long.class);
        List<SeedbankSummary> items = jdbc.query(SUMMARY + REVIEWED + where
                        + " group by b.id order by review_count desc, overall desc, b.name limit :limit offset :offset",
                params, SUMMARY_MAPPER);
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Optional<SeedbankSummary> find(UUID seedbankId) {
        return jdbc.query(SUMMARY + ANY + " where b.id = :id group by b.id", new MapSqlParameterSource("id", seedbankId),
                SUMMARY_MAPPER).stream().findFirst();
    }

    @Override
    public PageResult<SeedbankReviewView> reviewsOf(UUID seedbankId, PageQuery page) {
        MapSqlParameterSource params = new MapSqlParameterSource("seedbankId", seedbankId)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String from = """
                  from reviews r
                  join users u on u.id = r.author_id and u.status = 'ACTIVE' and not r.hidden
                  join grow_diaries d on d.id = r.diary_id
                  join strains s on s.id = r.strain_id
                 where r.seedbank_id = :seedbankId
                """;
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        List<SeedbankReviewView> items = jdbc.query("""
                select r.id, u.username, case when d.visibility = 'PUBLIC' then d.id end as public_diary_id, s.name as strain,
                       r.seedbank_germination, r.seedbank_stability, r.seedbank_service, r.report, r.created_at
                """ + from + " order by r.created_at desc, r.id limit :limit offset :offset",
                params,
                (rs, rowNum) -> new SeedbankReviewView(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getObject("public_diary_id", UUID.class),
                        rs.getString("strain"),
                        rs.getInt("seedbank_germination"),
                        rs.getInt("seedbank_stability"),
                        rs.getInt("seedbank_service"),
                        rs.getString("report"),
                        rs.getTimestamp("created_at").toInstant()));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<SeedbankSummary> SUMMARY_MAPPER = (rs, rowNum) -> new SeedbankSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getLong("review_count"),
            rs.getBigDecimal("overall"),
            rs.getBigDecimal("germination"),
            rs.getBigDecimal("stability"),
            rs.getBigDecimal("service"));
}
