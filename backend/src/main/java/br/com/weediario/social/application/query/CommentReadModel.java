package br.com.weediario.social.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommentReadModel {

    /** Comentários visíveis (não ocultados por denúncia), mais antigos primeiro, como numa conversa. */
    PageResult<CommentView> visibleComments(UUID diaryId, Optional<UUID> viewerId, PageQuery page);

    /**
     * @param byDiaryAuthor destaque "autor do diário" na conversa
     * @param deletable     se quem está vendo pode apagar (autor do comentário ou dono do diário)
     */
    record CommentView(UUID id, String author, boolean byDiaryAuthor, String body, Instant createdAt, boolean deletable) {

        public CommentView asModerator() {
            return new CommentView(id, author, byDiaryAuthor, body, createdAt, true);
        }
    }
}
