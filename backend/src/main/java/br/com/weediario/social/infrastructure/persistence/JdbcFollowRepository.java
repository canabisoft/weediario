package br.com.weediario.social.infrastructure.persistence;

import br.com.weediario.social.domain.repository.FollowRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcFollowRepository implements FollowRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFollowRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void follow(UUID followerId, UUID followeeId, Instant at) {
        jdbc.update("""
                insert into grower_follows (follower_id, followee_id, created_at) values (:follower, :followee, :at)
                on conflict do nothing""",
                params(followerId, followeeId).addValue("at", Timestamp.from(at)));
    }

    @Override
    public void unfollow(UUID followerId, UUID followeeId) {
        jdbc.update("delete from grower_follows where follower_id = :follower and followee_id = :followee",
                params(followerId, followeeId));
    }

    @Override
    public long countFollowing(UUID followerId) {
        Long count = jdbc.queryForObject("select count(*) from grower_follows where follower_id = :follower",
                new MapSqlParameterSource("follower", followerId), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource params(UUID followerId, UUID followeeId) {
        return new MapSqlParameterSource("follower", followerId).addValue("followee", followeeId);
    }
}
