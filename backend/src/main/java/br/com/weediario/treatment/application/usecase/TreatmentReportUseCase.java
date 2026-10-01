package br.com.weediario.treatment.application.usecase;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.treatment.application.TreatmentAccess;
import br.com.weediario.treatment.application.port.ReportRenderer;
import br.com.weediario.treatment.application.query.TreatmentQueries;
import br.com.weediario.treatment.application.query.TreatmentQueries.BatchStock;
import br.com.weediario.treatment.application.query.TreatmentQueries.DoseEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.PatientSummary;
import br.com.weediario.treatment.application.query.TreatmentQueries.RatingEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.SideEffectView;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.application.query.TreatmentStats;
import br.com.weediario.treatment.application.query.TreatmentStats.DayPoint;
import br.com.weediario.treatment.application.usecase.TreatmentLogUseCase.Period;
import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.ConcentrationUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Intensity;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Relatório para levar à consulta: consolida o que o paciente registrou no período. Apresenta somas, médias e
 * contagens do próprio histórico, sem interpretação nem sugestão; a leitura clínica é do médico prescritor.
 */
public final class TreatmentReportUseCase {

    /** Para o relatório, "início" e "fim" do período são os primeiros e os últimos 7 dias. */
    static final int EDGE_DAYS = 7;

    private final TreatmentQueries queries;
    private final TreatmentAccess access;
    private final TreatmentLogUseCase log;
    private final ReportRenderer renderer;
    private final Clock clock;

    public TreatmentReportUseCase(TreatmentQueries queries, TreatmentAccess access, TreatmentLogUseCase log,
                                  ReportRenderer renderer, Clock clock) {
        this.queries = queries;
        this.access = access;
        this.log = log;
        this.renderer = renderer;
        this.clock = clock;
    }

    public byte[] pdf(UUID patientId, UUID userId, LocalDate from, LocalDate to) {
        return renderer.render(build(patientId, userId, from, to));
    }

    public TreatmentReport build(UUID patientId, UUID userId, LocalDate from, LocalDate to) {
        access.roleOn(patientId, userId);
        Period period = log.period(from, to, 30);
        ZoneId zone = clock.getZone();
        PatientSummary patient = queries.patientsOf(userId).stream()
                .filter(p -> p.id().equals(patientId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Paciente"));
        List<DoseEntry> doses = queries.doses(patientId, period.start(), period.end()).stream()
                .sorted(Comparator.comparing(DoseEntry::takenAt))
                .toList();
        List<RatingEntry> checkins = queries.checkins(patientId, period.start(), period.end());
        List<SideEffectView> loose = queries.looseSideEffects(patientId, period.start(), period.end());
        List<SymptomView> symptoms = queries.symptoms(patientId, true);
        Map<UUID, BatchStock> batches = queries.batches(patientId, true, period.start()).stream()
                .collect(Collectors.toMap(BatchStock::id, Function.identity()));

        List<DayPoint> days = TreatmentStats.daily(doses, checkins, period.from(), period.to(), zone);
        long daysWithDoses = days.stream().filter(day -> day.doses() > 0).count();
        BigDecimal thc = TreatmentStats.sum(doses.stream().map(DoseEntry::thcMg).toList());
        BigDecimal cbd = TreatmentStats.sum(doses.stream().map(DoseEntry::cbdMg).toList());
        Totals totals = new Totals(doses.size(), daysWithDoses, days.size(), thc, cbd,
                perDay(thc, daysWithDoses), perDay(cbd, daysWithDoses));

        List<RatingEntry> ratings = Stream.concat(doses.stream().flatMap(d -> d.ratings().stream()), checkins.stream())
                .toList();
        List<SymptomSummary> symptomSummaries = symptoms.stream()
                .map(symptom -> summarize(symptom, ratings, doses, period, zone))
                .filter(summary -> summary.ratings() > 0)
                .toList();

        return new TreatmentReport(patient.name(), period.from(), period.to(), clock.instant(), zone, totals,
                batchUsage(doses, batches), symptomSummaries,
                sideEffects(Stream.concat(doses.stream().flatMap(d -> d.sideEffects().stream()), loose.stream()).toList()),
                days, symptoms, doses);
    }

    private static BigDecimal perDay(BigDecimal total, long days) {
        return total == null || days == 0 ? null : total.divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP);
    }

    private static List<BatchUsage> batchUsage(List<DoseEntry> doses, Map<UUID, BatchStock> batches) {
        Map<UUID, List<DoseEntry>> byBatch = new LinkedHashMap<>();
        doses.forEach(dose -> byBatch.computeIfAbsent(dose.batchId(), id -> new ArrayList<>()).add(dose));
        List<BatchUsage> usage = new ArrayList<>();
        byBatch.forEach((batchId, list) -> {
            BatchStock batch = batches.get(batchId);
            DoseEntry first = list.get(0);
            MedicineForm form = first.form();
            usage.add(new BatchUsage(first.batchName(), form, batch == null ? null : batch.origin(),
                    batch == null ? null : batch.thc(), batch == null ? null : batch.cbd(),
                    form.concentrationUnit(), form.stockUnit(), list.size(),
                    TreatmentStats.sum(list.stream().map(DoseEntry::stockQuantity).toList()),
                    TreatmentStats.sum(list.stream().map(DoseEntry::thcMg).toList()),
                    TreatmentStats.sum(list.stream().map(DoseEntry::cbdMg).toList())));
        });
        return usage;
    }

    private static SymptomSummary summarize(SymptomView symptom, List<RatingEntry> all, List<DoseEntry> doses,
                                            Period period, ZoneId zone) {
        List<RatingEntry> ratings = all.stream().filter(r -> r.symptomId().equals(symptom.id())).toList();
        LocalDate firstEdge = period.from().plusDays(EDGE_DAYS - 1L);
        LocalDate lastEdge = period.to().minusDays(EDGE_DAYS - 1L);
        Function<RatingEntry, LocalDate> day = r -> r.recordedAt().atZone(zone).toLocalDate();
        List<Integer> before = new ArrayList<>();
        List<Integer> after = new ArrayList<>();
        for (DoseEntry dose : doses) {
            Map<RatingMoment, Integer> moments = new EnumMap<>(RatingMoment.class);
            dose.ratings().stream().filter(r -> r.symptomId().equals(symptom.id()))
                    .forEach(r -> moments.put(r.moment(), r.value()));
            if (moments.containsKey(RatingMoment.BEFORE) && moments.containsKey(RatingMoment.AFTER)) {
                before.add(moments.get(RatingMoment.BEFORE));
                after.add(moments.get(RatingMoment.AFTER));
            }
        }
        return new SymptomSummary(symptom.name(), symptom.kind(), ratings.size(),
                TreatmentStats.average(ratings.stream().filter(r -> !day.apply(r).isAfter(firstEdge))
                        .map(RatingEntry::value).toList()),
                TreatmentStats.average(ratings.stream().filter(r -> !day.apply(r).isBefore(lastEdge))
                        .map(RatingEntry::value).toList()),
                before.size(), TreatmentStats.average(before), TreatmentStats.average(after),
                symptom.kind() == SymptomKind.COUNT ? ratings.stream().mapToInt(RatingEntry::value).sum() : null);
    }

    private static List<SideEffectCount> sideEffects(List<SideEffectView> entries) {
        Map<SideEffect, int[]> counts = new EnumMap<>(SideEffect.class);
        for (SideEffectView entry : entries) {
            counts.computeIfAbsent(entry.effect(), e -> new int[Intensity.values().length])[entry.intensity().ordinal()]++;
        }
        return counts.entrySet().stream()
                .map(e -> new SideEffectCount(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingInt(SideEffectCount::total).reversed())
                .toList();
    }

    // ----------------------------------------------------------------------------------------- dados

    public record TreatmentReport(String patientName, LocalDate from, LocalDate to, Instant generatedAt, ZoneId zone,
                                  Totals totals, List<BatchUsage> batches, List<SymptomSummary> symptoms,
                                  List<SideEffectCount> sideEffects, List<DayPoint> days, List<SymptomView> symptomList,
                                  List<DoseEntry> doses) {
    }

    /** Médias por dia consideram só os dias em que houve uso registrado. */
    public record Totals(int doses, long daysWithDoses, int days, BigDecimal thcMg, BigDecimal cbdMg,
                         BigDecimal thcMgPerDay, BigDecimal cbdMgPerDay) {
    }

    public record BatchUsage(String name, MedicineForm form, Origin origin, BigDecimal thc, BigDecimal cbd,
                             ConcentrationUnit concentrationUnit, StockUnit stockUnit, int doses, BigDecimal stockUsed,
                             BigDecimal thcMg, BigDecimal cbdMg) {
    }

    /**
     * @param firstWeekAverage média das notas nos primeiros 7 dias do período
     * @param lastWeekAverage  média das notas nos últimos 7 dias do período
     * @param pairs            doses com nota de antes e de depois
     * @param total            soma das contagens (só para sintomas do tipo contagem)
     */
    public record SymptomSummary(String name, SymptomKind kind, int ratings, BigDecimal firstWeekAverage,
                                 BigDecimal lastWeekAverage, int pairs, BigDecimal averageBefore,
                                 BigDecimal averageAfter, Integer total) {
    }

    public record SideEffectCount(SideEffect effect, int mild, int moderate, int severe) {

        public int total() {
            return mild + moderate + severe;
        }
    }
}
