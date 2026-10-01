package br.com.weediario.catalog.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido (ou vínculo aprovado) de alguém que representa um breeder do catálogo e pode cadastrar as sementes dele
 * para teste. A moderação aprova olhando a nota (prova de vínculo, como o site oficial); recusar ou revogar apaga
 * o pedido. Quem representa um breeder nunca é exibido publicamente.
 *
 * @param note visível só para a moderação
 */
public record BreederRepresentative(UUID breederId, UUID userId, Status status, String note, Instant requestedAt,
                                    Instant decidedAt) {

    public static final int NOTE_MIN_LENGTH = 10;
    public static final int NOTE_MAX_LENGTH = 500;
    /** Pedidos em aberto por pessoa (anti-spam). */
    public static final int MAX_PENDING_PER_USER = 5;

    public enum Status {
        PENDING,
        APPROVED
    }

    public BreederRepresentative {
        Guard.notNull(breederId, "Breeder");
        Guard.notNull(userId, "Usuário");
        Guard.notNull(status, "Status");
        Guard.notNull(requestedAt, "Data do pedido");
        if ((status == Status.APPROVED) != (decidedAt != null)) {
            throw new DomainException("Data da aprovação inconsistente");
        }
    }

    public static BreederRepresentative request(UUID breederId, UUID userId, String note, Clock clock) {
        String cleaned = Guard.notBlank(note, "Nota para a moderação").replaceAll("[\\p{Cntrl}&&[^\n]]", "").strip();
        Guard.lengthBetween(cleaned, NOTE_MIN_LENGTH, NOTE_MAX_LENGTH, "Nota para a moderação");
        return new BreederRepresentative(breederId, userId, Status.PENDING, cleaned, clock.instant(), null);
    }

    public BreederRepresentative approve(Clock clock) {
        if (approved()) {
            throw new DomainException("Este pedido já foi aprovado");
        }
        return new BreederRepresentative(breederId, userId, Status.APPROVED, note, requestedAt, clock.instant());
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }
}
