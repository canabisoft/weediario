package br.com.weediario.treatment.application.usecase;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.query.TreatmentQueries.DoseEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.RatingEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.SideEffectView;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.application.query.TreatmentStats;
import br.com.weediario.treatment.application.query.TreatmentStats.DayPoint;
import br.com.weediario.treatment.domain.model.Dose;
import br.com.weediario.treatment.domain.model.MedicineBatch;
import br.com.weediario.treatment.domain.model.SideEffectEntry;
import br.com.weediario.treatment.domain.model.Symptom;
import br.com.weediario.treatment.domain.model.SymptomRating;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.AccessRole;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.repository.TreatmentRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O diário do tratamento: registrar o que foi usado e como o paciente se sentiu, e consultar o histórico.
 * <p>
 * Tudo aqui é registro do que já aconteceu. O sistema não sugere dose, horário, produto nem ajuste.
 */
public final class TreatmentLogUseCase {

    /** Doses mais antigas que isso deixam de pedir a nota de "depois". */
    static final Duration AFTER_RATING_WINDOW = Duration.ofHours(12);
    public static final int MAX_PERIOD_DAYS = 366;
    private static final int MAX_RATINGS = 12;
    private static final int MAX_SIDE_EFFECTS = 10;

    private final TreatmentRepository repository;
    private final TreatmentQueries queries;
    private final TreatmentAccess access;
    private final MedicinesUseCase medicines;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public TreatmentLogUseCase(TreatmentRepository repository, TreatmentQueries queries, TreatmentAccess access,
                               MedicinesUseCase medicines, UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.queries = queries;
        this.access = access;
        this.medicines = medicines;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    // -------------------------------------------------------------------------------------- registro

    public UUID recordDose(UUID patientId, UUID userId, DoseCommand command) {
        limit(command.before(), MAX_RATINGS, "notas");
        limit(command.sideEffects(), MAX_SIDE_EFFECTS, "efeitos");
        return unitOfWork.execute(() -> {
            access.roleOn(patientId, userId);
            MedicineBatch batch = repository.findBatch(command.batchId())
                    .filter(b -> b.patientId().equals(patientId))
                    .orElseThrow(() -> new ResourceNotFoundException("Remédio"));
            Dose dose = repository.saveDose(Dose.record(batch, command.amount(), command.unit(), command.route(),
                    command.takenAt(), command.notes(), userId, clock));
            saveRatings(patientId, dose, RatingMoment.BEFORE, command.before(), userId);
            for (SideEffectInput effect : nullSafe(command.sideEffects())) {
                repository.saveSideEffect(SideEffectEntry.of(patientId, dose.id(), effect.effect(), effect.intensity(),
                        userId, clock));
            }
            return dose.id();
        });
    }

    /** Apagar uma dose registrada por engano devolve a quantidade ao estoque e apaga as notas ligadas a ela. */
    public void deleteDose(UUID patientId, UUID doseId, UUID userId) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            repository.deleteDose(dose(patientId, doseId).id());
        });
    }

    /** Nota de "depois" de uma dose. Registrar de novo corrige a nota anterior. */
    public void rateAfter(UUID patientId, UUID doseId, UUID userId, List<RatingInput> ratings) {
        limit(ratings, MAX_RATINGS, "notas");
        if (nullSafe(ratings).isEmpty()) {
            throw new DomainException("Informe ao menos uma nota");
        }
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            saveRatings(patientId, dose(patientId, doseId), RatingMoment.AFTER, ratings, userId);
        });
    }

    /** Check-in sem dose: como foi o dia, quantas crises, nota do sono. */
    public void checkin(UUID patientId, UUID userId, List<RatingInput> ratings) {
        limit(ratings, MAX_RATINGS, "notas");
        if (nullSafe(ratings).isEmpty()) {
            throw new DomainException("Informe ao menos uma nota");
        }
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            saveRatings(patientId, null, RatingMoment.CHECKIN, ratings, userId);
        });
    }

    public void deleteRating(UUID patientId, UUID ratingId, UUID userId) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            repository.deleteRating(ratingId, patientId);
        });
    }

    public UUID addSideEffect(UUID patientId, UUID userId, UUID doseId, SideEffect effect, Intensity intensity) {
        return unitOfWork.execute(() -> {
            access.roleOn(patientId, userId);
            if (doseId != null) {
                dose(patientId, doseId);
            }
            SideEffectEntry entry = SideEffectEntry.of(patientId, doseId, effect, intensity, userId, clock);
            repository.saveSideEffect(entry);
            return entry.id();
        });
    }

    public void deleteSideEffect(UUID patientId, UUID entryId, UUID userId) {
        unitOfWork.run(() -> {
            access.roleOn(patientId, userId);
            SideEffectEntry entry = repository.findSideEffect(entryId)
                    .filter(e -> e.patientId().equals(patientId))
                    .orElseThrow(() -> new ResourceNotFoundException("Registro"));
            repository.deleteSideEffect(entry.id());
        });
    }

    // -------------------------------------------------------------------------------------- consulta

    /** Tela inicial do paciente: remédios com estoque, usos de hoje e notas de "depois" que faltam. */
    public Today today(UUID patientId, UUID userId) {
        AccessRole role = access.roleOn(patientId, userId);
        ZoneId zone = clock.getZone();
        Instant now = clock.instant();
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(zone).toInstant();
        Instant since = startOfDay.isBefore(now.minus(AFTER_RATING_WINDOW)) ? startOfDay : now.minus(AFTER_RATING_WINDOW);
        List<DoseEntry> recent = queries.doses(patientId, since, now.plus(Duration.ofMinutes(10)));
        List<SymptomView> symptoms = queries.symptoms(patientId, false);
        Map<UUID, SymptomView> active = symptoms.stream().collect(Collectors.toMap(SymptomView::id, Function.identity()));

        List<PendingAfter> pending = recent.stream()
                .filter(dose -> dose.takenAt().isAfter(now.minus(AFTER_RATING_WINDOW)))
                .map(dose -> {
                    Set<UUID> rated = dose.ratings().stream().filter(r -> r.moment() == RatingMoment.AFTER)
                            .map(RatingEntry::symptomId).collect(Collectors.toSet());
                    List<SymptomView> missing = dose.ratings().stream()
                            .filter(r -> r.moment() == RatingMoment.BEFORE && !rated.contains(r.symptomId()))
                            .map(r -> active.get(r.symptomId()))
                            .filter(s -> s != null)
                            .distinct()
                            .toList();
                    return new PendingAfter(dose.id(), dose.batchName(), dose.takenAt(), missing);
                })
                .filter(p -> !p.symptoms().isEmpty())
                .toList();
        List<DoseEntry> todays = recent.stream().filter(dose -> !dose.takenAt().isBefore(startOfDay)).toList();
        return new Today(role, symptoms, medicines.list(patientId, userId, false), todays, pending,
                TreatmentStats.sum(todays.stream().map(DoseEntry::thcMg).toList()),
                TreatmentStats.sum(todays.stream().map(DoseEntry::cbdMg).toList()));
    }

    public Timeline timeline(UUID patientId, UUID userId, LocalDate from, LocalDate to) {
        access.roleOn(patientId, userId);
        Period period = period(from, to, 30);
        return new Timeline(period.from(), period.to(),
                queries.doses(patientId, period.start(), period.end()),
                queries.checkins(patientId, period.start(), period.end()),
                queries.looseSideEffects(patientId, period.start(), period.end()));
    }

    public Series series(UUID patientId, UUID userId, LocalDate from, LocalDate to) {
        access.roleOn(patientId, userId);
        Period period = period(from, to, 30);
        List<DoseEntry> doses = queries.doses(patientId, period.start(), period.end());
        List<RatingEntry> checkins = queries.checkins(patientId, period.start(), period.end());
        return new Series(period.from(), period.to(), queries.symptoms(patientId, true),
                TreatmentStats.daily(doses, checkins, period.from(), period.to(), clock.getZone()));
    }

    /** Período em dias locais; sem datas, os últimos {@code defaultDays} dias até hoje. */
    Period period(LocalDate from, LocalDate to, int defaultDays) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(defaultDays - 1L) : from;
        if (start.isAfter(end)) {
            throw new DomainException("O início do período é depois do fim");
        }
        if (start.plusDays(MAX_PERIOD_DAYS).isBefore(end)) {
            throw new DomainException("Período máximo de " + MAX_PERIOD_DAYS + " dias");
        }
        ZoneId zone = clock.getZone();
        return new Period(start, end, start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    record Period(LocalDate from, LocalDate to, Instant start, Instant end) {
    }

    // ------------------------------------------------------------------------------------- auxiliares

    private void saveRatings(UUID patientId, Dose dose, RatingMoment moment, List<RatingInput> ratings, UUID userId) {
        Set<UUID> seen = new HashSet<>();
        for (RatingInput input : nullSafe(ratings)) {
            if (!seen.add(input.symptomId())) {
                throw new DomainException("Sintoma repetido");
            }
            Symptom symptom = repository.findSymptom(input.symptomId())
                    .filter(s -> s.patientId().equals(patientId))
                    .orElseThrow(() -> new ResourceNotFoundException("Sintoma"));
            repository.saveRating(SymptomRating.of(symptom, dose, moment, input.value(), userId, clock));
        }
    }

    private Dose dose(UUID patientId, UUID doseId) {
        return repository.findDose(doseId)
                .filter(dose -> dose.patientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("Dose"));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void limit(List<?> list, int max, String what) {
        if (list != null && list.size() > max) {
            throw new DomainException("Muitas " + what + " num registro só");
        }
    }

    // -------------------------------------------------------------------------------------- comandos

    public record DoseCommand(UUID batchId, BigDecimal amount, DoseUnit unit, Route route, Instant takenAt,
                              String notes, List<RatingInput> before, List<SideEffectInput> sideEffects) {
    }

    public record RatingInput(UUID symptomId, int value) {
    }

    public record SideEffectInput(SideEffect effect, Intensity intensity) {
    }

    // ----------------------------------------------------------------------------------------- views

    /**
     * @param pendingAfter doses recentes com nota de "antes" e sem a de "depois" (o app pergunta, não insiste)
     */
    public record Today(AccessRole role, List<SymptomView> symptoms, List<MedicinesUseCase.BatchCard> medicines,
                        List<DoseEntry> doses, List<PendingAfter> pendingAfter, BigDecimal thcMgToday,
                        BigDecimal cbdMgToday) {
    }

    public record PendingAfter(UUID doseId, String batchName, Instant takenAt, List<SymptomView> symptoms) {
    }

    public record Timeline(LocalDate from, LocalDate to, List<DoseEntry> doses, List<RatingEntry> checkins,
                           List<SideEffectView> sideEffects) {
    }

    public record Series(LocalDate from, LocalDate to, List<SymptomView> symptoms, List<DayPoint> days) {
    }
}
