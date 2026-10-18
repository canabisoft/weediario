package br.com.weediario.social.infrastructure.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.social.application.query.GrowerDirectory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Estatísticas por grower calculadas só sobre diários públicos de contas ativas.
 * "Gramas por planta" é a soma do peso seco dividida pela soma de plantas das colheitas registradas.
 * Avaliações contam todas as publicadas (e não retidas pela moderação): elas já são públicas na página da strain,
 * mesmo quando o diário de origem é privado.
 */
@Component
public class JdbcGrowerDirectory implements GrowerDirectory {

    // :includeCommunity decide se os diários "comunidade" entram nas contagens: sempre no ranking (só logado o vê) e,
    // no perfil (público), apenas quando quem olha está logado. Deslogado conta só PUBLIC.
    private static final String VISIBLE = "(d.visibility = 'PUBLIC' or (d.visibility = 'COMMUNITY' and :includeCommunity))";
    private static final String STATS = """
            select u.id, u.username, case when u.profile_hidden then null else u.bio end as bio,
                   u.avatar_image_id is not null and not u.profile_hidden as has_avatar,
                   (select count(*) from grow_diaries d
                     where d.owner_id = u.id and %1$s) as diaries,
                   (select count(*) from diary_reactions r join grow_diaries d on d.id = r.diary_id
                     where d.owner_id = u.id and %1$s) as likes,
                   (select count(*) from grow_diaries d
                     where d.owner_id = u.id and %1$s and d.harvest_dry_grams is not null)
                       as harvests,
                   (select round(sum(d.harvest_dry_grams)::numeric / nullif(sum(d.harvest_plants), 0), 1)
                      from grow_diaries d
                     where d.owner_id = u.id and %1$s and d.harvest_dry_grams is not null)
                       as grams_per_plant,
                   (select count(*) from reviews r where r.author_id = u.id and not r.hidden) as reviews
              from users u
             where u.status = 'ACTIVE'
            """.formatted(VISIBLE);

    /** Única parte dinâmica do SQL, escolhida por enum (nunca texto do usuário). */
    private static final Map<RankingSort, String> METRIC = Map.of(
            RankingSort.DIARIES, "diaries",
            RankingSort.LIKES, "likes",
            RankingSort.GRAMS_PER_PLANT, "grams_per_plant",
            RankingSort.REVIEWS, "reviews");

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGrowerDirectory(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<RankedGrower> ranking(RankingSort sort, String usernameQuery, PageQuery page) {
        String metric = METRIC.get(sort);
        // A posição é calculada sobre todos os growers antes do filtro por nome: "#7" continua 7º na busca.
        // No ranking de avaliações entra quem avaliou, mesmo sem diário público (os diários podem ser privados).
        String ranked = """
                select s.*, rank() over (order by %s desc nulls last) as position
                  from (%s) s
                 where %s > 0
                """.formatted(metric, STATS, sort == RankingSort.REVIEWS ? "s.reviews" : "s.diaries");
        MapSqlParameterSource params = new MapSqlParameterSource()
                // O ranking só abre para quem está logado, então os diários "comunidade" sempre contam aqui.
                .addValue("includeCommunity", true)
                .addValue("limit", page.size())
                .addValue("offset", page.offset());
        String filter = "";
        if (usernameQuery != null) {
            filter = " where r.username ilike :query escape '\\'";
            params.addValue("query", "%" + escapeLike(usernameQuery) + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from (" + ranked + ") r" + filter, params, Long.class);
        List<RankedGrower> items = jdbc.query(
                "select * from (" + ranked + ") r" + filter + " order by r.position, lower(r.username) limit :limit offset :offset",
                params,
                (rs, rowNum) -> new RankedGrower(
                        rs.getLong("position"),
                        rs.getString("username"),
                        rs.getBoolean("has_avatar"),
                        rs.getLong("diaries"),
                        rs.getLong("likes"),
                        rs.getBigDecimal("grams_per_plant"),
                        rs.getLong("reviews")));
        return PageResult.of(items, page, total == null ? 0 : total);
    }

    @Override
    public Optional<GrowerProfile> profile(String username, Optional<UUID> viewerId) {
        MapSqlParameterSource params = new MapSqlParameterSource("username", username)
                // Perfil é público: diários "comunidade" só entram nas contagens para quem está logado.
                .addValue("includeCommunity", viewerId.isPresent())
                .addValue("viewerId", viewerId.orElse(null), Types.OTHER);
        return jdbc.query("""
                select s.*,
                       (select count(*) from grower_follows f where f.followee_id = s.id) as followers,
                       exists(select 1 from grower_follows f
                               where f.followee_id = s.id and f.follower_id = :viewerId) as followed_by_viewer,
                       coalesce(s.id = :viewerId, false) as is_viewer
                  from (%s) s
                 where lower(s.username) = lower(:username)""".formatted(STATS),
                params,
                (rs, rowNum) -> new GrowerProfile(
                        rs.getString("username"),
                        rs.getString("bio"),
                        rs.getBoolean("has_avatar"),
                        rs.getLong("diaries"),
                        rs.getLong("likes"),
                        rs.getLong("harvests"),
                        rs.getBigDecimal("grams_per_plant"),
                        rs.getLong("reviews"),
                        rs.getLong("followers"),
                        rs.getBoolean("followed_by_viewer"),
                        rs.getBoolean("is_viewer"))).stream().findFirst();
    }

    @Override
    public List<FollowedGrower> followedBy(UUID followerId) {
        return jdbc.query("""
                select u.username, u.avatar_image_id is not null and not u.profile_hidden as has_avatar
                  from grower_follows f
                  join users u on u.id = f.followee_id and u.status = 'ACTIVE'
                 where f.follower_id = :follower
                 order by lower(u.username)""",
                new MapSqlParameterSource("follower", followerId),
                (rs, rowNum) -> new FollowedGrower(rs.getString("username"), rs.getBoolean("has_avatar")));
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
