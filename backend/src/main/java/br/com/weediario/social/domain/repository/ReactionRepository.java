package br.com.weediario.social.domain.repository;

import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.model.ReactorPseudonym;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ReactionRepository {

    /**
     * Idempotente: reagir duas vezes com o mesmo tipo não duplica.
     *
     * @return {@code true} se a reação é nova
     */
    boolean add(UUID diaryId, ReactorPseudonym reactor, ReactionType type, Instant at);

    void remove(UUID diaryId, ReactorPseudonym reactor, ReactionType type);

    Map<ReactionType, Long> countByType(UUID diaryId);

    Set<ReactionType> typesBy(UUID diaryId, ReactorPseudonym reactor);
}
