package br.com.weediario.social.domain.repository;

import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.model.ReactorPseudonym;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reações anônimas nos comentários dos diários: guarda só o pseudônimo de quem reagiu (diferente em cada
 * comentário), nunca o id do usuário.
 */
public interface CommentReactionRepository {

    /** Idempotente: reagir duas vezes com o mesmo tipo não duplica. */
    void add(UUID commentId, ReactorPseudonym reactor, ReactionType type);

    void remove(UUID commentId, ReactorPseudonym reactor, ReactionType type);

    /** Contagem por tipo de cada comentário pedido (comentários sem reação ficam de fora do mapa). */
    Map<UUID, Map<ReactionType, Long>> countByType(Collection<UUID> commentIds);

    /** Reações de quem está vendo, a partir do pseudônimo dele em cada comentário. */
    Map<UUID, Set<ReactionType>> typesBy(Map<UUID, ReactorPseudonym> reactorByComment);
}
