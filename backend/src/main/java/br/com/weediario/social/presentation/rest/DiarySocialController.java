package br.com.weediario.social.presentation.rest;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import br.com.weediario.social.application.query.CommentReadModel.CommentReactions;
import br.com.weediario.social.application.query.CommentReadModel.CommentView;
import br.com.weediario.social.application.usecase.DiaryCommentsUseCase;
import br.com.weediario.social.application.usecase.DiaryReactionsUseCase;
import br.com.weediario.social.application.usecase.DiaryReactionsUseCase.ReactionSummary;
import br.com.weediario.social.domain.model.Comment;
import br.com.weediario.social.domain.model.ReactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diaries/{diaryId}")
public class DiarySocialController {

    private final DiaryCommentsUseCase comments;
    private final DiaryReactionsUseCase reactions;

    public DiarySocialController(DiaryCommentsUseCase comments, DiaryReactionsUseCase reactions) {
        this.comments = comments;
        this.reactions = reactions;
    }

    @GetMapping("/comments")
    public PageResult<CommentView> comments(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return comments.list(diaryId, Viewer.idOf(principal), Viewer.isModerator(principal), new PageQuery(page, size));
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> comment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                     @Valid @RequestBody CommentRequest request) {
        return Map.of("id", comments.post(diaryId, Viewer.requireId(principal), request.body(),
                Optional.ofNullable(request.parentId())));
    }

    /** "Ver mais": todas as respostas visíveis de um comentário raiz. */
    @GetMapping("/comments/{commentId}/replies")
    public PageResult<CommentView> replies(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                           @PathVariable UUID commentId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return comments.replies(diaryId, commentId, Viewer.idOf(principal), Viewer.isModerator(principal),
                new PageQuery(page, size));
    }

    /** Reação anônima num comentário: só contagens e as reações de quem está logado. */
    @PutMapping("/comments/{commentId}/reactions/{type}")
    public CommentReactions reactToComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                           @PathVariable UUID commentId, @PathVariable ReactionType type) {
        return comments.react(diaryId, commentId, Viewer.requireId(principal), type);
    }

    @DeleteMapping("/comments/{commentId}/reactions/{type}")
    public CommentReactions unreactToComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                             @PathVariable UUID commentId, @PathVariable ReactionType type) {
        return comments.unreact(diaryId, commentId, Viewer.requireId(principal), type);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                              @PathVariable UUID commentId) {
        comments.delete(diaryId, commentId, Viewer.requireId(principal));
    }

    /** Só contagens; {@code mine} traz apenas as reações de quem está logado. */
    @GetMapping("/reactions")
    public ReactionSummary reactions(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId) {
        return reactions.summary(diaryId, Viewer.idOf(principal));
    }

    @PutMapping("/reactions/{type}")
    public ReactionSummary react(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                 @PathVariable ReactionType type) {
        return reactions.react(diaryId, Viewer.requireId(principal), type);
    }

    @DeleteMapping("/reactions/{type}")
    public ReactionSummary unreact(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                   @PathVariable ReactionType type) {
        return reactions.unreact(diaryId, Viewer.requireId(principal), type);
    }

    /** @param parentId comentário respondido (opcional) */
    public record CommentRequest(@NotBlank @Size(max = Comment.BODY_MAX_LENGTH) String body, UUID parentId) {
    }
}
