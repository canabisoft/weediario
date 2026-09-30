package br.com.weediario.review.presentation.rest;

import br.com.weediario.review.application.query.BrowseStrainsUseCase;
import br.com.weediario.review.application.query.StrainCatalog.AuthoredReview;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GrowerReviewsController {

    private final BrowseStrainsUseCase browseStrains;

    public GrowerReviewsController(BrowseStrainsUseCase browseStrains) {
        this.browseStrains = browseStrains;
    }

    /** Reviews já são públicos na página de cada strain; aqui aparecem reunidos no perfil de quem escreveu. */
    @GetMapping("/api/v1/growers/{username}/reviews")
    public PageResult<AuthoredReview> reviews(@PathVariable @Size(max = 30) String username,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return browseStrains.reviewsBy(username, new PageQuery(page, size));
    }
}
