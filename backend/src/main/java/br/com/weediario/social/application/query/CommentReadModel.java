package br.com.weediario.social.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.social.domain.model.ReactionType;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura da conversa de um diário. Comentários de contas que não estão ativas ficam de fora (e, se for um comentário
 * raiz, as respostas dele também); os ocultados pela moderação só aparecem para quem escreveu.
 */
public interface CommentReadModel {

    /**
     * Comentários raiz visíveis, mais antigos primeiro, como numa conversa. Os ocultados pela moderação só aparecem
     * para quem escreveu (marcados como "em análise"). Vêm sem respostas e sem reações; quem chama completa.
     */
    PageResult<CommentView> visibleComments(UUID diaryId, Optional<UUID> viewerId, PageQuery page);

    /**
     * As primeiras {@code perRoot} respostas visíveis de cada comentário raiz, em ordem cronológica, e o total de
     * respostas visíveis de cada um. Comentários raiz sem respostas ficam de fora do mapa.
     */
    Map<UUID, Replies> replyPreviews(Collection<UUID> rootIds, Optional<UUID> viewerId, int perRoot);

    /** Respostas visíveis de um comentário raiz ("ver mais"); vazio se o comentário raiz não é visível. */
    PageResult<CommentView> visibleReplies(UUID rootId, Optional<UUID> viewerId, PageQuery page);

    record Replies(List<CommentView> items, long total) {
    }

    /**
     * Contagens por tipo e as reações de quem está vendo (para poder desfazer). Nunca diz quem reagiu.
     */
    record CommentReactions(Map<ReactionType, Long> counts, Set<ReactionType> mine) {

        public static CommentReactions of(Map<ReactionType, Long> counts, Set<ReactionType> mine) {
            Map<ReactionType, Long> all = new EnumMap<>(ReactionType.class);
            for (ReactionType type : ReactionType.values()) {
                all.put(type, counts.getOrDefault(type, 0L));
            }
            return new CommentReactions(all, Set.copyOf(mine));
        }
    }

    /**
     * @param parentId      comentário raiz respondido (nulo nos comentários raiz)
     * @param byDiaryAuthor destaque "autor do diário" na conversa
     * @param deletable     se quem está vendo pode apagar (autor do comentário ou dono do diário)
     * @param underReview   oculto do público aguardando a moderação (só o autor vê)
     * @param replyCount    total de respostas visíveis (só nos comentários raiz)
     * @param replies       as primeiras respostas, em ordem cronológica (o resto vem pelo "ver mais")
     */
    record CommentView(UUID id, UUID parentId, String author, boolean authorHasAvatar, boolean byDiaryAuthor,
                       String body, Instant createdAt, boolean deletable, boolean underReview,
                       CommentReactions reactions, long replyCount, List<CommentView> replies) {

        public CommentView asModerator() {
            return new CommentView(id, parentId, author, authorHasAvatar, byDiaryAuthor, body, createdAt, true,
                    underReview, reactions, replyCount, replies.stream().map(CommentView::asModerator).toList());
        }

        public CommentView withReactions(CommentReactions newReactions) {
            return new CommentView(id, parentId, author, authorHasAvatar, byDiaryAuthor, body, createdAt, deletable,
                    underReview, newReactions, replyCount, replies);
        }

        public CommentView withReplies(List<CommentView> newReplies, long total) {
            return new CommentView(id, parentId, author, authorHasAvatar, byDiaryAuthor, body, createdAt, deletable,
                    underReview, reactions, total, List.copyOf(newReplies));
        }
    }
}
