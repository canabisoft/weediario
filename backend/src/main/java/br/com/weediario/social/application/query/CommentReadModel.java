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
    /** Fila da moderação: comentários com denúncia, ocultados primeiro, depois os mais denunciados. */
    PageResult<ReportedComment> reportedComments(PageQuery page);

    /** Quantos comentários têm denúncia pendente e quantos deles já foram ocultados automaticamente. */
    ReportCounts reportCounts();

    /**
     * @param reports denúncias distintas (quem denunciou nunca é exposto, nem para a moderação)
     */
    record ReportedComment(UUID id, UUID diaryId, String diaryTitle, String author, String body, long reports,
                           boolean hidden, Instant createdAt) {
    }

    record ReportCounts(long reported, long hidden) {
    }

    record CommentView(UUID id, String author, boolean authorHasAvatar, boolean byDiaryAuthor, String body,
                       Instant createdAt, boolean deletable) {

        public CommentView asModerator() {
            return new CommentView(id, author, authorHasAvatar, byDiaryAuthor, body, createdAt, true);
        }
    }
}
