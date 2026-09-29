package br.com.weediario.review.presentation.rest;

import br.com.weediario.review.application.query.BrowseSeedbanksUseCase;
import br.com.weediario.review.application.query.SeedbankCatalog.SeedbankReviewView;
import br.com.weediario.review.application.query.SeedbankCatalog.SeedbankSummary;
import br.com.weediario.review.domain.model.Seedbank;
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
 * Bancos de sementes avaliados pela comunidade. Aberto a visitantes.
 */
@RestController
@RequestMapping("/api/v1/seedbanks")
public class SeedbankController {

    private final BrowseSeedbanksUseCase browseSeedbanks;

    public SeedbankController(BrowseSeedbanksUseCase browseSeedbanks) {
        this.browseSeedbanks = browseSeedbanks;
    }

    @GetMapping
    public PageResult<SeedbankSummary> search(
            @RequestParam(required = false) @Size(max = Seedbank.NAME_MAX_LENGTH) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return browseSeedbanks.search(query, new PageQuery(page, size));
    }

    @GetMapping("/{seedbankId}")
    public SeedbankSummary detail(@PathVariable UUID seedbankId) {
        return browseSeedbanks.detail(seedbankId);
    }

    @GetMapping("/{seedbankId}/reviews")
    public PageResult<SeedbankReviewView> reviews(@PathVariable UUID seedbankId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return browseSeedbanks.reviews(seedbankId, new PageQuery(page, size));
    }
}
