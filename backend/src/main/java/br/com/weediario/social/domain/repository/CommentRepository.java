package br.com.weediario.social.domain.repository;

import br.com.weediario.social.domain.model.Comment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(UUID id);

    void delete(UUID id);

    long countByAuthorSince(UUID authorId, Instant since);
}
