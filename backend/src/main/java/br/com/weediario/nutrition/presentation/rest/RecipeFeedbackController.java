package br.com.weediario.nutrition.presentation.rest;

import br.com.weediario.nutrition.application.query.RecipeReadModel.Recommendation;
import br.com.weediario.nutrition.application.usecase.RecipeFeedbackUseCase;
import br.com.weediario.nutrition.application.usecase.RecipeFeedbackUseCase.CommentReactions;
import br.com.weediario.nutrition.application.usecase.RecipeFeedbackUseCase.CommentView;
import br.com.weediario.nutrition.application.usecase.RecipeFeedbackUseCase.Rating;
import br.com.weediario.nutrition.application.usecase.RecommendRecipesUseCase;
import br.com.weediario.nutrition.domain.model.CommentReaction;
import br.com.weediario.nutrition.domain.model.RecipeComment;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Estrelas, comentários e recomendações de receitas. */
@RestController
public class RecipeFeedbackController {

    private final RecipeFeedbackUseCase feedback;
    private final RecommendRecipesUseCase recommend;

    public RecipeFeedbackController(RecipeFeedbackUseCase feedback, RecommendRecipesUseCase recommend) {
        this.feedback = feedback;
        this.recommend = recommend;
    }

    /**
     * Sugestões para quem está vendo. Com {@code diaryId} (cultivo do próprio grower), o tipo segue o meio do
     * cultivo e as receitas já vinculadas a ele ficam de fora.
     */
    @GetMapping("/api/v1/recipes/recommended")
    public List<Recommendation> recommended(@AuthenticationPrincipal Jwt principal,
                                            @RequestParam(required = false) UUID diaryId,
                                            @RequestParam(required = false) RecipeKind kind,
                                            @RequestParam(defaultValue = "6") int size) {
        return recommend.recommend(Viewer.idOf(principal), Optional.ofNullable(diaryId), kind, size);
    }

    @GetMapping("/api/v1/recipes/{id}/rating")
    public Rating rating(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return feedback.rating(id, Viewer.idOf(principal));
    }

    @PutMapping("/api/v1/recipes/{id}/rating")
    public Rating rate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                       @Valid @RequestBody RatingRequest request) {
        return feedback.rate(id, Viewer.requireId(principal), request.stars());
    }

    @DeleteMapping("/api/v1/recipes/{id}/rating")
    public Rating removeRating(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return feedback.removeRating(id, Viewer.requireId(principal));
    }

    @GetMapping("/api/v1/recipes/{id}/comments")
    public PageResult<CommentView> comments(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return feedback.comments(id, Viewer.idOf(principal), Viewer.isModerator(principal), new PageQuery(page, size));
    }

    @PostMapping("/api/v1/recipes/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                               @Valid @RequestBody CommentRequest request) {
        return feedback.postComment(id, Viewer.requireId(principal), request.body(),
                Optional.ofNullable(request.parentId()));
    }

    /** "Ver mais": todas as respostas visíveis de um comentário raiz. */
    @GetMapping("/api/v1/recipes/{id}/comments/{commentId}/replies")
    public PageResult<CommentView> replies(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                                           @PathVariable UUID commentId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return feedback.replies(id, commentId, Viewer.idOf(principal), Viewer.isModerator(principal),
                new PageQuery(page, size));
    }

    /** Reação anônima num comentário: só contagens e as reações de quem está logado. */
    @PutMapping("/api/v1/recipes/{id}/comments/{commentId}/reactions/{type}")
    public CommentReactions reactToComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                                           @PathVariable UUID commentId, @PathVariable CommentReaction type) {
        return feedback.react(id, commentId, Viewer.requireId(principal), type);
    }

    @DeleteMapping("/api/v1/recipes/{id}/comments/{commentId}/reactions/{type}")
    public CommentReactions unreactToComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id,
                                             @PathVariable UUID commentId, @PathVariable CommentReaction type) {
        return feedback.unreact(id, commentId, Viewer.requireId(principal), type);
    }

    @DeleteMapping("/api/v1/recipes/{id}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id, @PathVariable UUID commentId) {
        feedback.deleteComment(id, commentId, Viewer.requireId(principal), Viewer.isModerator(principal));
    }

    public record RatingRequest(@NotNull @Min(1) @Max(5) Integer stars) {
    }

    /** @param parentId comentário respondido (opcional) */
    public record CommentRequest(@NotBlank @Size(max = RecipeComment.BODY_MAX_LENGTH) String body, UUID parentId) {
    }
}
