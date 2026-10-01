package br.com.weediario.treatment.application.query;

import br.com.weediario.treatment.application.query.TreatmentQueries.DoseEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.RatingEntry;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Agregações por dia do que foi registrado: mg usados e notas dos sintomas. São só somas e médias do histórico,
 * sem interpretação clínica.
 */
public final class TreatmentStats {

    private TreatmentStats() {
    }

    /**
     * @param symptoms média do dia por sintoma (escala) ou soma (contagem); sintomas sem registro no dia ficam de fora
     */
    public record DayPoint(LocalDate day, int doses, BigDecimal thcMg, BigDecimal cbdMg, Map<UUID, BigDecimal> symptoms) {
    }

    public static List<DayPoint> daily(List<DoseEntry> doses, List<RatingEntry> checkins, LocalDate from, LocalDate to,
                                       ZoneId zone) {
        Map<LocalDate, List<DoseEntry>> dosesByDay = new TreeMap<>();
        for (DoseEntry dose : doses) {
            dosesByDay.computeIfAbsent(dose.takenAt().atZone(zone).toLocalDate(), d -> new ArrayList<>()).add(dose);
        }
        Map<LocalDate, List<RatingEntry>> ratingsByDay = new TreeMap<>();
        Stream.concat(doses.stream().flatMap(dose -> dose.ratings().stream()), checkins.stream())
                .forEach(rating -> ratingsByDay
                        .computeIfAbsent(rating.recordedAt().atZone(zone).toLocalDate(), d -> new ArrayList<>())
                        .add(rating));

        List<DayPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<DoseEntry> ofDay = dosesByDay.getOrDefault(day, List.of());
            points.add(new DayPoint(day, ofDay.size(),
                    sum(ofDay.stream().map(DoseEntry::thcMg).toList()),
                    sum(ofDay.stream().map(DoseEntry::cbdMg).toList()),
                    symptomValues(ratingsByDay.getOrDefault(day, List.of()))));
        }
        return points;
    }

    /** Escala: média do dia. Contagem: total do dia. */
    static Map<UUID, BigDecimal> symptomValues(List<RatingEntry> ratings) {
        Map<UUID, List<RatingEntry>> bySymptom = new LinkedHashMap<>();
        ratings.forEach(rating -> bySymptom.computeIfAbsent(rating.symptomId(), id -> new ArrayList<>()).add(rating));
        Map<UUID, BigDecimal> values = new LinkedHashMap<>();
        bySymptom.forEach((symptom, list) -> {
            int total = list.stream().mapToInt(RatingEntry::value).sum();
            values.put(symptom, list.get(0).kind() == SymptomKind.COUNT
                    ? BigDecimal.valueOf(total)
                    : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP));
        });
        return values;
    }

    /** Soma ignorando valores desconhecidos; nulo se nenhum valor era conhecido. */
    public static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = null;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total == null ? value : total.add(value);
            }
        }
        return total;
    }

    /** Média com uma casa, ou nulo para lista vazia. */
    public static BigDecimal average(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        int total = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }
}
