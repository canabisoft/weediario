package br.com.weediario.social.presentation.rest;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import br.com.weediario.social.application.query.CommentReadModel.ReportCounts;
import br.com.weediario.social.application.query.CommentReadModel.ReportedComment;
import br.com.weediario.social.application.usecase.ModerateCommentsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/moderation/comments")
public class CommentModerationController {

    private final ModerateCommentsUseCase moderation;

    public CommentModerationController(ModerateCommentsUseCase moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    public PageResult<ReportedComment> reported(@AuthenticationPrincipal Jwt principal,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return moderation.reported(new PageQuery(page, size), Viewer.isModerator(principal));
    }

    @GetMapping("/counts")
    public ReportCounts counts(@AuthenticationPrincipal Jwt principal) {
        return moderation.counts(Viewer.isModerator(principal));
    }

    /** Denúncias improcedentes: mantém o comentário visível. */
    @PostMapping("/{commentId}/keep")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void keep(@AuthenticationPrincipal Jwt principal, @PathVariable UUID commentId) {
        moderation.keep(commentId, Viewer.isModerator(principal));
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt principal, @PathVariable UUID commentId) {
        moderation.remove(commentId, Viewer.isModerator(principal));
    }
}
