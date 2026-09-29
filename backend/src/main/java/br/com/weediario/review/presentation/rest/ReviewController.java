package br.com.weediario.review.presentation.rest;

import br.com.weediario.review.application.usecase.SubmitReviewUseCase;
import br.com.weediario.review.application.usecase.SubmitReviewUseCase.SubmittedReview;
import br.com.weediario.review.presentation.rest.dto.SubmitReviewRequest;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ReviewController {

    private final SubmitReviewUseCase submitReview;

    public ReviewController(SubmitReviewUseCase submitReview) {
        this.submitReview = submitReview;
    }

    @PostMapping("/api/v1/diaries/{diaryId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmittedReview submit(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                  @Valid @RequestBody SubmitReviewRequest request) {
        return submitReview.execute(new SubmitReviewUseCase.Command(
                diaryId,
                Viewer.requireId(principal),
                request.vigor(),
                request.pestResistance(),
                request.easeOfGrowth(),
                request.yield(),
                request.terpeneProfile(),
                request.seedbankGermination(),
                request.seedbankStability(),
                request.seedbankService(),
                request.report()));
    }
}
