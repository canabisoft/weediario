package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação de uma genética, sempre ancorada em um cultivo real e concluído do próprio autor.
 * Isso dá credibilidade às notas (não existe review "de quem nunca plantou") e limita spam.
 */
public final class Review {

    public static final int REPORT_MIN_LENGTH = 20;
    public static final int REPORT_MAX_LENGTH = 10_000;

    private final UUID id;
    private final StrainId strainId;
    private final UUID diaryId;
    private final UUID authorId;
    private final Ratings ratings;
    private final String report;
    private final Instant createdAt;

    private Review(UUID id, StrainId strainId, UUID diaryId, UUID authorId, Ratings ratings, String report,
                   Instant createdAt) {
        this.id = Guard.notNull(id, "Id");
        this.strainId = Guard.notNull(strainId, "Strain");
        this.diaryId = Guard.notNull(diaryId, "Diário");
        this.authorId = Guard.notNull(authorId, "Autor");
        this.ratings = Guard.notNull(ratings, "Notas");
        this.report = validReport(report);
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
    }

    public static Review submit(ReviewedGrow grow, UUID authorId, Strain strain, Ratings ratings, String report,
                                Clock clock) {
        if (!grow.ownerId().equals(authorId)) {
            throw new DomainException("Apenas o autor do diário pode avaliar o cultivo");
        }
        if (!grow.completed()) {
            throw new DomainException("Só é possível avaliar a genética depois de concluir o cultivo");
        }
        return new Review(UUID.randomUUID(), strain.id(), grow.diaryId(), authorId, ratings, report, clock.instant());
    }

    public static Review restore(UUID id, StrainId strainId, UUID diaryId, UUID authorId, Ratings ratings,
                                 String report, Instant createdAt) {
        return new Review(id, strainId, diaryId, authorId, ratings, report, createdAt);
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

    public String report() {
        return report;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
