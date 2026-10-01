package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockAdjustmentKind;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Correção de estoque fora das doses: recontagem (±) ou descarte (sempre negativo). */
public record StockAdjustment(UUID id, UUID batchId, StockAdjustmentKind kind, BigDecimal quantity, Instant occurredAt,
                              UUID recordedBy) {

    private static final BigDecimal MAX = BigDecimal.valueOf(100_000);

    public StockAdjustment {
        Guard.notNull(id, "Id");
        Guard.notNull(batchId, "Remédio");
        Guard.notNull(kind, "Tipo");
        Guard.notNull(quantity, "Quantidade");
        if (quantity.signum() == 0 || quantity.abs().compareTo(MAX) > 0
                || (kind == StockAdjustmentKind.DISCARD && quantity.signum() > 0)) {
            throw new DomainException("Quantidade do ajuste inválida");
        }
        Guard.notNull(occurredAt, "Data");
    }

    public static StockAdjustment of(MedicineBatch batch, StockAdjustmentKind kind, BigDecimal quantity,
                                     UUID recordedBy, Clock clock) {
        Guard.notNull(quantity, "Quantidade");
        BigDecimal signed = kind == StockAdjustmentKind.DISCARD ? quantity.abs().negate() : quantity;
        return new StockAdjustment(UUID.randomUUID(), batch.id(), kind, signed, clock.instant(), recordedBy);
    }
}
