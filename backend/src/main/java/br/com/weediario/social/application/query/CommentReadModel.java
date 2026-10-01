package br.com.weediario.social.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CommentReadModel {

    /**
     * Comentários visíveis, mais antigos primeiro, como numa conversa. Os ocultados pela moderação só aparecem para
     * quem escreveu (marcados como "em análise").
     */
    PageResult<CommentView> visibleComments(UUID diaryId, Optional<UUID> viewerId, PageQuery page);

    /**
     * @param byDiaryAuthor destaque "autor do diário" na conversa
     * @param deletable     se quem está vendo pode apagar (autor do comentário ou dono do diário)
     * @param underReview   oculto do público aguardando a moderação (só o autor vê)
     */
    record CommentView(UUID id, String author, boolean authorHasAvatar, boolean byDiaryAuthor, String body,
                       Instant createdAt, boolean deletable, boolean underReview) {

        public CommentView asModerator() {
            return new CommentView(id, author, authorHasAvatar, byDiaryAuthor, body, createdAt, true, underReview);
        }
    }
}
