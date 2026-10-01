package br.com.weediario.social.domain.repository;

import br.com.weediario.social.domain.model.Comment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(UUID id);

    /** Se quem escreveu o comentário tem conta ativa (comentário de conta suspensa ou banida sai do ar). */
    boolean authorActive(UUID commentId);

    void delete(UUID id);

    long countByAuthorSince(UUID authorId, Instant since);
}
