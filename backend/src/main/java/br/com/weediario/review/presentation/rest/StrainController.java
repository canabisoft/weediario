package br.com.weediario.review.presentation.rest;

import br.com.weediario.review.application.query.BrowseStrainsUseCase;
import br.com.weediario.review.application.query.StrainCatalog.ReviewView;
import br.com.weediario.review.application.query.StrainCatalog.StrainSummary;
import br.com.weediario.review.domain.model.Strain;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Catálogo de genéticas avaliadas pela comunidade. Aberto a visitantes.
 */
@RestController
@RequestMapping("/api/v1/strains")
public class StrainController {

    private final BrowseStrainsUseCase browseStrains;

    public StrainController(BrowseStrainsUseCase browseStrains) {
        this.browseStrains = browseStrains;
    }

    @GetMapping
    public PageResult<StrainSummary> search(@RequestParam(required = false) @Size(max = Strain.NAME_MAX_LENGTH) String query,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return browseStrains.search(query, new PageQuery(page, size));
    }

    @GetMapping("/{strainId}")
    public StrainSummary detail(@PathVariable UUID strainId) {
        return browseStrains.detail(strainId);
    }

    @GetMapping("/{strainId}/reviews")
    public PageResult<ReviewView> reviews(@PathVariable UUID strainId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return browseStrains.reviews(strainId, new PageQuery(page, size));
    }
}
