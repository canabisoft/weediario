package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.BreederKind;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido (ou vínculo aprovado) de alguém que representa um breeder do catálogo e pode cadastrar as sementes dele
 * para teste. Vale para bancos de sementes e para breeders independentes (quem cria genéticas por conta própria).
 * A moderação aprova olhando a nota (prova de vínculo: site oficial, diários de polinização, perfil onde o trabalho
 * é divulgado); recusar ou revogar apaga o pedido.
 * <p>
 * Quem representa um breeder não é exibido publicamente, a não ser que um breeder independente escolha aparecer
 * como criador(a) das genéticas ({@code publicCredit}).
 *
 * @param note         visível só para a moderação
 * @param claimedKind  o que a pessoa diz representar; ao aprovar, o breeder passa a ter esse tipo
 * @param publicCredit só para breeder independente: mostrar o apelido como criador(a)
 */
public record BreederRepresentative(UUID breederId, UUID userId, Status status, String note, Instant requestedAt,
                                    Instant decidedAt, BreederKind claimedKind, boolean publicCredit) {

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
        claimedKind = claimedKind == null ? BreederKind.SEEDBANK : claimedKind;
        requireCreditAllowed(claimedKind, publicCredit);
    }

    public static BreederRepresentative request(UUID breederId, UUID userId, String note, BreederKind claimedKind,
                                                boolean publicCredit, Clock clock) {
        String cleaned = Guard.notBlank(note, "Nota para a moderação").replaceAll("[\\p{Cntrl}&&[^\n]]", "").strip();
        Guard.lengthBetween(cleaned, NOTE_MIN_LENGTH, NOTE_MAX_LENGTH, "Nota para a moderação");
        return new BreederRepresentative(breederId, userId, Status.PENDING, cleaned, clock.instant(), null,
                Guard.notNull(claimedKind, "O que você representa"), publicCredit);
    }

    /** Pedido de quem representa um banco de sementes, sem crédito público. */
    public static BreederRepresentative request(UUID breederId, UUID userId, String note, Clock clock) {
        return request(breederId, userId, note, BreederKind.SEEDBANK, false, clock);
    }

    public BreederRepresentative approve(Clock clock) {
        if (approved()) {
            throw new DomainException("Este pedido já foi aprovado");
        }
        return new BreederRepresentative(breederId, userId, Status.APPROVED, note, requestedAt, clock.instant(),
                claimedKind, publicCredit);
    }

    /** Liga ou desliga o crédito público (a pessoa decide, a qualquer momento). */
    public BreederRepresentative withPublicCredit(boolean credit) {
        return new BreederRepresentative(breederId, userId, status, note, requestedAt, decidedAt, claimedKind, credit);
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }

    private static void requireCreditAllowed(BreederKind kind, boolean publicCredit) {
        if (publicCredit && kind != BreederKind.INDEPENDENT) {
            throw new DomainException("Só breeders independentes aparecem como criadores das genéticas");
        }
    }
}
