package br.com.weediario.review.application.query;

import br.com.weediario.review.application.query.SeedbankCatalog.SeedbankReviewView;
import br.com.weediario.review.application.query.SeedbankCatalog.SeedbankSummary;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;

import java.util.UUID;

public final class BrowseSeedbanksUseCase {

    private final SeedbankCatalog catalog;

    public BrowseSeedbanksUseCase(SeedbankCatalog catalog) {
        this.catalog = catalog;
    }

    public PageResult<SeedbankSummary> search(String nameQuery, PageQuery page) {
        return catalog.search(nameQuery == null || nameQuery.isBlank() ? null : nameQuery.strip(), page);
    }

    public SeedbankSummary detail(UUID seedbankId) {
        return catalog.find(seedbankId).orElseThrow(() -> new ResourceNotFoundException("Banco de sementes"));
    }

    public PageResult<SeedbankReviewView> reviews(UUID seedbankId, PageQuery page) {
        return catalog.reviewsOf(seedbankId, page);
    }
}
