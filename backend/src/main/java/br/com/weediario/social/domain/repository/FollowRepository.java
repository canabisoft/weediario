package br.com.weediario.social.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface FollowRepository {

    /**
     * Idempotente.
     *
     * @return {@code true} se passou a seguir agora (não seguia antes)
     */
    boolean follow(UUID followerId, UUID followeeId, Instant at);

    void unfollow(UUID followerId, UUID followeeId);

    long countFollowing(UUID followerId);
}
