package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Avaliação de uma genética, sempre ancorada em um cultivo real e concluído do próprio autor.
 * Isso dá credibilidade às notas (não existe review "de quem nunca plantou") e limita spam.
 */
public final class Review {

    public static final int REPORT_MIN_LENGTH = 20;
    public static final int REPORT_MAX_LENGTH = 10_000;
    /** Mais do que cabe num setup (20 produtos), com folga para os informados direto no diário. */
    public static final int MAX_PRODUCT_RATINGS = 40;

    private final UUID id;
    private final StrainId strainId;
    private final UUID diaryId;
    private final UUID authorId;
    private final Ratings ratings;
    private final UUID seedbankId;
    private final SeedbankRatings seedbankRatings;
    private final List<ProductRating> productRatings;
    private final String report;
    private final Instant createdAt;
    /** Oculto do público, aguardando a moderação (filtro anti-comércio ou denúncias). */
    private boolean hidden;

    private Review(UUID id, StrainId strainId, UUID diaryId, UUID authorId, Ratings ratings, UUID seedbankId,
                   SeedbankRatings seedbankRatings, List<ProductRating> productRatings, String report,
                   Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.strainId = Guard.notNull(strainId, "Strain");
        this.diaryId = Guard.notNull(diaryId, "Diário");
        this.authorId = Guard.notNull(authorId, "Autor");
        this.ratings = Guard.notNull(ratings, "Notas");
        if ((seedbankId == null) != (seedbankRatings == null)) {
            throw new DomainException("Avaliação do banco de sementes incompleta");
        }
        this.seedbankId = seedbankId;
        this.seedbankRatings = seedbankRatings;
        this.productRatings = productRatings == null ? List.of() : List.copyOf(productRatings);
        if (this.productRatings.size() > MAX_PRODUCT_RATINGS) {
            throw new DomainException("No máximo " + MAX_PRODUCT_RATINGS + " produtos por avaliação");
        }
        this.report = validReport(report);
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Review submit(ReviewedGrow grow, UUID authorId, Ratings ratings, SeedbankRatings seedbankRatings,
                                String report, Clock clock) {
        return submit(grow, authorId, ratings, seedbankRatings, List.of(), report, clock);
    }

    /**
     * A genética, o banco e os produtos avaliados são sempre os do diário (vêm do catálogo).
     *
     * @param seedbankRatings notas do banco de sementes; opcionais
     * @param productRatings  notas dos produtos usados no cultivo; opcionais, uma por produto
     */
    public static Review submit(ReviewedGrow grow, UUID authorId, Ratings ratings, SeedbankRatings seedbankRatings,
                                List<ProductRating> productRatings, String report, Clock clock) {
        if (!grow.ownerId().equals(authorId)) {
            throw new DomainException("Apenas o autor do diário pode avaliar o cultivo");
        }
        if (!grow.completed()) {
            throw new DomainException("Só é possível avaliar a genética depois de concluir o cultivo");
        }
        if (grow.strainId() == null) {
            throw new DomainException("Este diário não está ligado a uma strain do catálogo");
        }
        if (seedbankRatings != null && grow.breederId() == null) {
            throw new DomainException("O diário não informa o banco de sementes, então ele não pode ser avaliado");
        }
        List<ProductRating> products = productRatings == null ? List.of() : productRatings;
        Set<UUID> rated = new HashSet<>();
        for (ProductRating product : products) {
            if (!grow.productIds().contains(product.productId())) {
                throw new DomainException("Só dá para avaliar os produtos usados neste cultivo");
            }
            if (!rated.add(product.productId())) {
                throw new DomainException("Cada produto só pode receber uma nota");
            }
        }
        return new Review(UUID.randomUUID(), new StrainId(grow.strainId()), grow.diaryId(), authorId, ratings,
                seedbankRatings == null ? null : grow.breederId(), seedbankRatings, products, report, clock.instant());
    }

    public static Review restore(UUID id, StrainId strainId, UUID diaryId, UUID authorId, Ratings ratings,
                                 UUID seedbankId, SeedbankRatings seedbankRatings, String report, Instant createdAt) {
        return new Review(id, strainId, diaryId, authorId, ratings, seedbankId, seedbankRatings, List.of(), report,
                createdAt);
    }

    public static Review restore(UUID id, StrainId strainId, UUID diaryId, UUID authorId, Ratings ratings,
                                 UUID seedbankId, SeedbankRatings seedbankRatings, List<ProductRating> productRatings,
                                 String report, Instant createdAt, boolean hidden) {
        Review review = new Review(id, strainId, diaryId, authorId, ratings, seedbankId, seedbankRatings, productRatings,
                report, createdAt);
        review.hidden = hidden;
        return review;
    }

    /** Retida pelo filtro anti-comércio: salva, mas fora do público até a moderação decidir. */
    public void holdForModeration() {
        hidden = true;
    }

    public boolean hidden() {
        return hidden;
    }

    /** Texto guardado como veio (o Angular faz o escape); só removemos caracteres de controle. */
    private static String validReport(String report) {
        String cleaned = Guard.notBlank(report, "Relato").replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        return Guard.lengthBetween(cleaned, REPORT_MIN_LENGTH, REPORT_MAX_LENGTH, "Relato");
    }

    public UUID id() {
        return id;
    }

    public StrainId strainId() {
        return strainId;
    }

    public UUID diaryId() {
        return diaryId;
    }

    public UUID authorId() {
        return authorId;
    }

    public Ratings ratings() {
        return ratings;
    }

    public Optional<UUID> seedbankId() {
        return Optional.ofNullable(seedbankId);
    }

    public Optional<SeedbankRatings> seedbankRatings() {
        return Optional.ofNullable(seedbankRatings);
    }

    public List<ProductRating> productRatings() {
        return productRatings;
    }

    public String report() {
        return report;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
