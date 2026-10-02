package br.com.weediario.social.application.usecase;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.social.application.port.CommentModeration;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.application.port.InteractionNotifier;
import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.application.query.CommentReadModel;
import br.com.weediario.social.application.query.CommentReadModel.CommentReactions;
import br.com.weediario.social.application.query.CommentReadModel.CommentView;
import br.com.weediario.social.application.query.CommentReadModel.Replies;
import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.model.CommentableDiary;
import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.model.ReactorPseudonym;
import br.com.weediario.social.domain.repository.CommentReactionRepository;
import br.com.weediario.social.domain.repository.CommentRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Comentários nos diários, com limite de frequência por conta (anti-flood sem precisar de IP), ocultação automática
 * por denúncias, respostas em um nível e reações anônimas.
 * <p>
 * Responder uma resposta anexa a nova ao mesmo comentário raiz (ver {@link Comment#reply}); quem é avisado é o autor
 * do comentário respondido. Apagar um comentário raiz apaga as respostas junto.
 */
public final class DiaryCommentsUseCase {

    public static final int MAX_PER_MINUTE = 5;
    public static final int MAX_PER_DAY = 200;
    /** Respostas que já vêm na listagem debaixo de cada comentário raiz; o resto vem pelo "ver mais". */
    public static final int REPLY_PREVIEW = 5;
    /** Separa o pseudônimo de reação do pseudônimo de denúncia do mesmo comentário. */
    static final String REACTION_PURPOSE = "diary-comment-reaction";

    private final DiaryAudience diaries;
    private final CommentRepository comments;
    private final CommentReactionRepository reactions;
    private final CommentReadModel readModel;
    private final CommentModeration moderation;
    private final InteractionNotifier notifier;
    private final Pseudonymizer pseudonymizer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DiaryCommentsUseCase(DiaryAudience diaries, CommentRepository comments, CommentReactionRepository reactions,
                                CommentReadModel readModel, CommentModeration moderation, InteractionNotifier notifier,
                                Pseudonymizer pseudonymizer, UnitOfWork unitOfWork, Clock clock) {
        this.diaries = diaries;
        this.comments = comments;
        this.reactions = reactions;
        this.readModel = readModel;
        this.moderation = moderation;
        this.notifier = notifier;
        this.pseudonymizer = pseudonymizer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Comentários raiz paginados, cada um com as primeiras respostas e as reações. */
    public PageResult<CommentView> list(UUID diaryId, Optional<UUID> viewerId, boolean moderator, PageQuery page) {
        return unitOfWork.execute(() -> {
            visibleDiary(diaryId, viewerId);
            PageResult<CommentView> roots = readModel.visibleComments(diaryId, viewerId, page);
            Map<UUID, Replies> previews = readModel.replyPreviews(
                    roots.items().stream().map(CommentView::id).toList(), viewerId, REPLY_PREVIEW);
            PageResult<CommentView> threads = roots.map(root -> {
                Replies replies = previews.get(root.id());
                return replies == null ? root : root.withReplies(replies.items(), replies.total());
            });
            PageResult<CommentView> result = withReactions(threads, viewerId);
            return moderator ? result.map(CommentView::asModerator) : result;
        });
    }

    /** Todas as respostas visíveis de um comentário raiz ("ver mais"), em ordem cronológica. */
    public PageResult<CommentView> replies(UUID diaryId, UUID commentId, Optional<UUID> viewerId, boolean moderator,
                                           PageQuery page) {
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, viewerId);
            Comment comment = commentOf(diary, commentId);
            PageResult<CommentView> result = withReactions(readModel.visibleReplies(comment.rootId(), viewerId, page),
                    viewerId);
            return moderator ? result.map(CommentView::asModerator) : result;
        });
    }

    public UUID post(UUID diaryId, UUID authorId, String body) {
        return post(diaryId, authorId, body, Optional.empty());
    }

    /**
     * Filtro anti-comércio: contato/PIX é bloqueado; cara de anúncio é salvo oculto e vai para a moderação
     * (sem notificar ninguém enquanto isso). Vale igual para respostas.
     *
     * @param answeredId comentário respondido (vazio para um comentário novo na conversa)
     */
    public UUID post(UUID diaryId, UUID authorId, String body, Optional<UUID> answeredId) {
        Verdict verdict = CommerceFilter.requireAllowedOrHeld(body);
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(authorId));
            ensureWithinRateLimit(authorId);
            Optional<Comment> answered = answeredId.map(id -> visibleComment(diary, id, authorId));
            Comment comment = answered
                    .map(target -> Comment.reply(diary, target, authorId, body, clock))
                    .orElseGet(() -> Comment.post(diary, authorId, body, clock));
            if (verdict.held()) {
                comment.holdForModeration();
            }
            UUID id = comments.save(comment).id();
            if (verdict.held()) {
                moderation.heldByFilter(id, verdict.reason());
            } else if (answered.isPresent()) {
                UUID answeredAuthor = answered.get().authorId();
                notifier.replied(diary, answeredAuthor, authorId);
                // O dono do diário continua sabendo da conversa, sem aviso duplicado quando a resposta é para ele.
                if (!diary.isOwnedBy(answeredAuthor)) {
                    notifier.commentPosted(diary, authorId);
                }
            } else {
                notifier.commentPosted(diary, authorId);
            }
            return id;
        });
    }

    /** Apagar um comentário raiz apaga as respostas dele junto. */
    public void delete(UUID diaryId, UUID commentId, UUID requesterId) {
        unitOfWork.run(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(requesterId));
            Comment comment = commentOf(diary, commentId);
            if (!comment.canBeDeletedBy(requesterId, diary)) {
                throw new ResourceNotFoundException("Comentário");
            }
            comments.delete(comment.id());
        });
    }

    /** Reação anônima num comentário visível: só o pseudônimo (usuário, comentário) é guardado. */
    public CommentReactions react(UUID diaryId, UUID commentId, UUID userId, ReactionType type) {
        return unitOfWork.execute(() -> {
            Comment comment = visibleComment(visibleDiary(diaryId, Optional.of(userId)), commentId, userId);
            reactions.add(comment.id(), reactorOf(userId, comment.id()), type);
            return reactionsOf(comment.id(), userId);
        });
    }

    public CommentReactions unreact(UUID diaryId, UUID commentId, UUID userId, ReactionType type) {
        return unitOfWork.execute(() -> {
            Comment comment = visibleComment(visibleDiary(diaryId, Optional.of(userId)), commentId, userId);
            reactions.remove(comment.id(), reactorOf(userId, comment.id()), type);
            return reactionsOf(comment.id(), userId);
        });
    }

    private CommentReactions reactionsOf(UUID commentId, UUID userId) {
        Map<ReactionType, Long> counts = reactions.countByType(List.of(commentId)).getOrDefault(commentId, Map.of());
        Set<ReactionType> mine = reactions.typesBy(Map.of(commentId, reactorOf(userId, commentId)))
                .getOrDefault(commentId, Set.of());
        return CommentReactions.of(counts, mine);
    }

    /** Completa as contagens de reação dos comentários e das respostas da página (e as reações de quem vê). */
    private PageResult<CommentView> withReactions(PageResult<CommentView> page, Optional<UUID> viewerId) {
        List<UUID> ids = new ArrayList<>();
        page.items().forEach(view -> {
            ids.add(view.id());
            view.replies().forEach(reply -> ids.add(reply.id()));
        });
        Map<UUID, Map<ReactionType, Long>> counts = reactions.countByType(ids);
        Map<UUID, Set<ReactionType>> mine = viewerId.map(viewer -> {
            Map<UUID, ReactorPseudonym> reactors = new HashMap<>();
            ids.forEach(id -> reactors.put(id, reactorOf(viewer, id)));
            return reactions.typesBy(reactors);
        }).orElse(Map.of());
        return page.map(view -> withReactions(view, counts, mine));
    }

    private static CommentView withReactions(CommentView view, Map<UUID, Map<ReactionType, Long>> counts,
                                             Map<UUID, Set<ReactionType>> mine) {
        List<CommentView> replies = view.replies().stream().map(reply -> withReactions(reply, counts, mine)).toList();
        return view.withReplies(replies, view.replyCount())
                .withReactions(CommentReactions.of(counts.getOrDefault(view.id(), Map.of()),
                        mine.getOrDefault(view.id(), Set.of())));
    }

    private ReactorPseudonym reactorOf(UUID userId, UUID commentId) {
        return pseudonymizer.pseudonymOf(userId, commentId, REACTION_PURPOSE);
    }

    private void ensureWithinRateLimit(UUID authorId) {
        Instant now = clock.instant();
        if (comments.countByAuthorSince(authorId, now.minus(Duration.ofMinutes(1))) >= MAX_PER_MINUTE
                || comments.countByAuthorSince(authorId, now.minus(Duration.ofDays(1))) >= MAX_PER_DAY) {
            throw new DomainException("Muitos comentários em pouco tempo. Tente novamente em instantes.");
        }
    }

    private CommentableDiary visibleDiary(UUID diaryId, Optional<UUID> viewerId) {
        return diaries.find(diaryId)
                .filter(diary -> diary.isVisibleTo(viewerId))
                .orElseThrow(() -> new ResourceNotFoundException("Diário"));
    }

    private Comment commentOf(CommentableDiary diary, UUID commentId) {
        return comments.findById(commentId)
                .filter(comment -> comment.diaryId().equals(diary.diaryId()))
                .orElseThrow(() -> new ResourceNotFoundException("Comentário"));
    }

    /** O comentário e, se for resposta, o comentário raiz dele precisam estar visíveis para quem interage. */
    private Comment visibleComment(CommentableDiary diary, UUID commentId, UUID viewerId) {
        Comment comment = commentOf(diary, commentId);
        // Mesmo critério da listagem: oculto só para o autor, e nada de conta suspensa ou banida (nem no raiz).
        boolean visible = comment.isVisibleTo(viewerId) && comments.authorActive(comment.id()) && comment.parentId()
                .map(rootId -> comments.findById(rootId)
                        .map(root -> root.isVisibleTo(viewerId) && comments.authorActive(rootId)).orElse(false))
                .orElse(true);
        if (!visible) {
            throw new ResourceNotFoundException("Comentário");
        }
        return comment;
    }
}
