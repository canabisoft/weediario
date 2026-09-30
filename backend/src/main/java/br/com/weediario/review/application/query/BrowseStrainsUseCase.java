package br.com.weediario.review.application.query;

import br.com.weediario.review.application.query.StrainCatalog.AuthoredReview;
import br.com.weediario.review.application.query.StrainCatalog.ReviewView;
import br.com.weediario.review.application.query.StrainCatalog.StrainSummary;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;

import java.util.UUID;

public final class BrowseStrainsUseCase {

    private final StrainCatalog catalog;

    public BrowseStrainsUseCase(StrainCatalog catalog) {
        this.catalog = catalog;
    }

    public PageResult<StrainSummary> search(String nameQuery, PageQuery page) {
        return catalog.search(nameQuery == null || nameQuery.isBlank() ? null : nameQuery.strip(), page);
    }

    public StrainSummary detail(UUID strainId) {
        return catalog.find(strainId).orElseThrow(() -> new ResourceNotFoundException("Strain"));
    }

    public PageResult<AuthoredReview> reviewsBy(String username, PageQuery page) {
        return catalog.reviewsBy(username, page);
    }

    /** Lista vazia para strains do catálogo que ainda não receberam review. */
    public PageResult<ReviewView> reviews(UUID strainId, PageQuery page) {
        return catalog.reviewsOf(strainId, page);
    }
}
