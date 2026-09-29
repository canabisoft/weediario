package br.com.weediario.review.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura dos bancos de sementes avaliados pela comunidade.
 */
public interface SeedbankCatalog {

    PageResult<SeedbankSummary> search(String nameQuery, PageQuery page);

    Optional<SeedbankSummary> find(UUID seedbankId);

    PageResult<SeedbankReviewView> reviewsOf(UUID seedbankId, PageQuery page);

    record SeedbankSummary(UUID id, String name, long reviewCount, BigDecimal overall, BigDecimal germination,
                           BigDecimal stability, BigDecimal service) {
    }

    /**
     * @param diaryId preenchido só se o diário for público
     */
    record SeedbankReviewView(UUID id, String author, UUID diaryId, String strain, int germination, int stability,
                              int service, String report, Instant createdAt) {
    }
}
