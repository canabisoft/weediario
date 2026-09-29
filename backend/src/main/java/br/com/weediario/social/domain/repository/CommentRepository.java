package br.com.weediario.social.domain.repository;

import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.model.ReactorPseudonym;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(UUID id);

    void delete(UUID id);

    long countByAuthorSince(UUID authorId, Instant since);

    /** Registra a denúncia e devolve o total de denúncias distintas do comentário. Denúncia repetida é ignorada. */
    long report(UUID commentId, ReactorPseudonym reporter, Instant at);
}
