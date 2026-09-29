package br.com.weediario.social.application.port;

import br.com.weediario.social.domain.model.CommentableDiary;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta ao contexto de diários (anti-corruption layer).
 */
public interface DiaryAudience {

    Optional<CommentableDiary> find(UUID diaryId);
}
