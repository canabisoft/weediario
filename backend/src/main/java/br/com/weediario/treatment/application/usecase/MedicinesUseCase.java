package br.com.weediario.treatment.application.usecase;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.query.TreatmentQueries.BatchStock;
import br.com.weediario.treatment.domain.model.MedicineBatch;
import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.StockAdjustment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.ConcentrationUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockAdjustmentKind;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockUnit;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Remédios prontos do paciente e o estoque de cada lote.
 */
public final class MedicinesUseCase {

    /** Janela usada para estimar quantos dias o estoque ainda dura no ritmo atual. */
    static final Duration USAGE_WINDOW = Duration.ofDays(14);
    /** Validade a menos disto aparece como "vence em breve". */
    static final int EXPIRING_SOON_DAYS = 30;

    private final TreatmentRepository repository;
    private final TreatmentQueries queries;
    private final TreatmentAccess access;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public MedicinesUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                            UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.queries = queries;
        this.access = access;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<BatchCard> list(UUID patientId, UUID userId, boolean includeArchived) {
        access.roleOn(patientId, userId);
        LocalDate today = LocalDate.now(clock);
        return queries.batches(patientId, includeArchived, clock.instant().minus(USAGE_WINDOW)).stream()
                .map(batch -> BatchCard.of(batch, today))
                .toList();
    }

    public UUID register(UUID patientId, UUID userId, Details details) {
        return unitOfWork.execute(() -> {
            access.roleOn(patientId, userId);
            return repository.saveBatch(MedicineBatch.register(patientId, details.name(), details.form(),
                    details.origin() == null ? Origin.OTHER : details.origin(), details.thc(), details.cbd(),
                    details.dropsPerMl(), details.initialQuantity(), details.manufacturedOn(), details.expiresOn(),
                    details.notes(), clock)).id();
        });
    }

    /** Edita o rótulo; forma e quantidade inicial ficam como estão (correções de estoque viram ajustes). */
    public void update(UUID patientId, UUID batchId, UUID userId, Details details) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            MedicineBatch batch = batch(patientId, batchId);
            repository.saveBatch(batch.withDetails(details.name(), details.origin() == null ? batch.origin() : details.origin(),
                    details.thc(), details.cbd(), details.dropsPerMl(), details.manufacturedOn(), details.expiresOn(),
                    details.notes()));
        });
    }

    public void archive(UUID patientId, UUID batchId, UUID userId, boolean archived) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            repository.saveBatch(batch(patientId, batchId).archive(archived));
        });
    }

    public void adjust(UUID patientId, UUID batchId, UUID userId, StockAdjustmentKind kind, BigDecimal quantity) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            repository.saveAdjustment(StockAdjustment.of(batch(patientId, batchId), kind, quantity, userId, clock));
        });
    }

    private MedicineBatch batch(UUID patientId, UUID batchId) {
        return repository.findBatch(batchId)
                .filter(batch -> batch.patientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Remédio"));
    }

    public record Details(String name, MedicineForm form, Origin origin, BigDecimal thc, BigDecimal cbd,
                          BigDecimal dropsPerMl, BigDecimal initialQuantity, LocalDate manufacturedOn,
                          LocalDate expiresOn, String notes) {
    }

    /**
     * Lote como aparece no painel.
     *
     * @param estimatedDaysLeft quantos dias o saldo dura no ritmo de uso das últimas duas semanas (só estoque, não é
     *                          orientação de uso); nulo sem uso recente
     */
    public record BatchCard(UUID id, String name, MedicineForm form, Origin origin, BigDecimal thc, BigDecimal cbd,
                            ConcentrationUnit concentrationUnit, StockUnit stockUnit, Set<DoseUnit> doseUnits,
                            BigDecimal dropsPerMl, BigDecimal initialQuantity, BigDecimal remaining,
                            Integer estimatedDaysLeft, boolean expired, boolean expiresSoon, LocalDate manufacturedOn,
                            LocalDate expiresOn, String notes, boolean archived) {

        static BatchCard of(BatchStock batch, LocalDate today) {
            Integer daysLeft = null;
            if (batch.usedRecently().signum() > 0 && batch.remaining().signum() > 0) {
                BigDecimal perDay = batch.usedRecently().divide(BigDecimal.valueOf(USAGE_WINDOW.toDays()), 6, RoundingMode.HALF_UP);
                daysLeft = batch.remaining().divide(perDay, 0, RoundingMode.DOWN).intValue();
            }
            boolean expired = batch.expiresOn() != null && batch.expiresOn().isBefore(today);
            boolean soon = !expired && batch.expiresOn() != null
                    && !batch.expiresOn().isAfter(today.plusDays(EXPIRING_SOON_DAYS));
            return new BatchCard(batch.id(), batch.name(), batch.form(), batch.origin(), batch.thc(), batch.cbd(),
                    batch.form().concentrationUnit(), batch.form().stockUnit(), batch.form().doseUnits(),
                    batch.dropsPerMl(), batch.initialQuantity(), batch.remaining(), daysLeft, expired, soon,
                    batch.manufacturedOn(), batch.expiresOn(), batch.notes(), batch.archived());
        }
    }
}
