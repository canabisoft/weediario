package br.com.weediario.social.application.usecase;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.application.port.InteractionNotifier;
import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.application.query.CommentReadModel;
import br.com.weediario.social.application.query.CommentReadModel.CommentView;
import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.model.CommentableDiary;
import br.com.weediario.social.domain.repository.CommentRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Comentários nos diários, com limite de frequência por conta (anti-flood sem precisar de IP)
 * e ocultação automática por denúncias.
 */
public final class DiaryCommentsUseCase {

    public static final int MAX_PER_MINUTE = 5;
    public static final int MAX_PER_DAY = 200;

    private final DiaryAudience diaries;
    private final CommentRepository comments;
    private final CommentReadModel readModel;
    private final Pseudonymizer pseudonymizer;
    private final InteractionNotifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DiaryCommentsUseCase(DiaryAudience diaries, CommentRepository comments, CommentReadModel readModel,
                                Pseudonymizer pseudonymizer, InteractionNotifier notifier, UnitOfWork unitOfWork,
                                Clock clock) {
        this.diaries = diaries;
        this.comments = comments;
        this.readModel = readModel;
        this.pseudonymizer = pseudonymizer;
        this.notifier = notifier;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public PageResult<CommentView> list(UUID diaryId, Optional<UUID> viewerId, boolean moderator, PageQuery page) {
        return unitOfWork.execute(() -> {
            visibleDiary(diaryId, viewerId);
            PageResult<CommentView> result = readModel.visibleComments(diaryId, viewerId, page);
            return moderator ? result.map(CommentView::asModerator) : result;
        });
    }

    public UUID post(UUID diaryId, UUID authorId, String body) {
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(authorId));
            ensureWithinRateLimit(authorId);
            UUID id = comments.save(Comment.post(diary, authorId, body, clock)).id();
            notifier.commentPosted(diary, authorId);
            return id;
        });
    }

    public void delete(UUID diaryId, UUID commentId, UUID requesterId, boolean moderator) {
        unitOfWork.run(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(requesterId));
            Comment comment = commentOf(diary, commentId);
            if (!comment.canBeDeletedBy(requesterId, diary, moderator)) {
                throw new ResourceNotFoundException("Comentário");
            }
            comments.delete(comment.id());
        });
    }

    public void report(UUID diaryId, UUID commentId, UUID reporterId) {
        unitOfWork.run(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(reporterId));
            Comment comment = commentOf(diary, commentId);
            if (!comment.canBeReportedBy(reporterId)) {
                throw new DomainException("Não é possível denunciar o próprio comentário");
            }
            long reports = comments.report(comment.id(), pseudonymizer.pseudonymOf(reporterId, comment.id()),
                    clock.instant());
            comment.registerReports(reports);
            comments.save(comment);
        });
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
}
