package br.com.weediario.review.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura do catálogo: médias agregadas por genética e as reviews publicadas.
 * Só entram reviews de contas ativas.
 */
public interface StrainCatalog {

    PageResult<StrainSummary> search(String nameQuery, PageQuery page);

    Optional<StrainSummary> find(UUID strainId);

    PageResult<ReviewView> reviewsOf(UUID strainId, PageQuery page);

    /** Reviews publicados por um grower (perfil público), mais recentes primeiro. */
    PageResult<AuthoredReview> reviewsBy(String username, PageQuery page);

    /** Cultivos (não cancelados) da pessoa com esta strain, mais recentes primeiro: dizem se ela já pode avaliar. */
    List<OwnGrow> growsOf(UUID strainId, UUID ownerId);

    record StrainSummary(
            UUID id,
            String name,
            String breeder,
            long reviewCount,
            BigDecimal overall,
            BigDecimal vigor,
            BigDecimal pestResistance,
            BigDecimal easeOfGrowth,
            BigDecimal yield,
            BigDecimal terpeneProfile) {
    }

    /**
     * @param overall média dos cinco critérios da strain
     * @param diaryId preenchido só se o diário for público; review de diário privado não leva até ele
     */
    record AuthoredReview(
            UUID id,
            UUID strainId,
            String strain,
            String breeder,
            UUID diaryId,
            BigDecimal overall,
            String report,
            Instant createdAt) {
    }

    /**
     * @param completed cultivo concluído (só esses podem ser avaliados)
     * @param reviewed  o cultivo já tem avaliação publicada
     */
    record OwnGrow(UUID diaryId, String title, boolean completed, boolean reviewed) {
    }

    /**
     * @param diaryId preenchido só se o diário for público; review de diário privado não leva até ele
     */
    record ReviewView(
            UUID id,
            String author,
            UUID diaryId,
            int vigor,
            int pestResistance,
            int easeOfGrowth,
            int yield,
            int terpeneProfile,
            String report,
            Instant createdAt) {
    }
}
