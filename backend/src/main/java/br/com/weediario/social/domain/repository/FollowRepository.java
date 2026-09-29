package br.com.weediario.social.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface FollowRepository {

    /** Idempotente. */
    void follow(UUID followerId, UUID followeeId, Instant at);

    void unfollow(UUID followerId, UUID followeeId);

    long countFollowing(UUID followerId);
}
