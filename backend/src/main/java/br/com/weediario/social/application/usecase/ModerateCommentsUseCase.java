package br.com.weediario.social.application.usecase;

import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.social.application.query.CommentReadModel;
import br.com.weediario.social.application.query.CommentReadModel.ReportCounts;
import br.com.weediario.social.application.query.CommentReadModel.ReportedComment;
import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.repository.CommentRepository;

import java.util.UUID;

/**
 * Moderação dos comentários denunciados. A moderação vê o comentário, o autor e quantas denúncias ele recebeu,
 * mas nunca quem denunciou (as denúncias guardam só um pseudônimo).
 */
public final class ModerateCommentsUseCase {

    private final CommentRepository comments;
    private final CommentReadModel readModel;
    private final UnitOfWork unitOfWork;

    public ModerateCommentsUseCase(CommentRepository comments, CommentReadModel readModel, UnitOfWork unitOfWork) {
        this.comments = comments;
        this.readModel = readModel;
        this.unitOfWork = unitOfWork;
    }

    public PageResult<ReportedComment> reported(PageQuery page, boolean moderator) {
        requireModerator(moderator);
        return readModel.reportedComments(page);
    }

    public ReportCounts counts(boolean moderator) {
        requireModerator(moderator);
        return readModel.reportCounts();
    }

    /** Denúncias improcedentes: o comentário volta a aparecer e as denúncias analisadas são descartadas. */
    public void keep(UUID commentId, boolean moderator) {
        requireModerator(moderator);
        unitOfWork.run(() -> {
            Comment comment = comment(commentId);
            comment.keepAfterReview();
            comments.save(comment);
            comments.clearReports(comment.id());
        });
    }

    public void remove(UUID commentId, boolean moderator) {
        requireModerator(moderator);
        unitOfWork.run(() -> comments.delete(comment(commentId).id()));
    }

    private Comment comment(UUID id) {
        return comments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comentário"));
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }
}
