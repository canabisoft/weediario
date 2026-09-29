package br.com.weediario.diary.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Idade do cultivo contada a partir do início do diário. O dia de início é o "dia 1" da "semana 1".
 */
public record GrowAge(long day, long week) {

    public static GrowAge fromElapsedDays(long elapsedDays) {
        long day = Math.max(elapsedDays, 0) + 1;
        return new GrowAge(day, (day - 1) / 7 + 1);
    }

    /** Depois do encerramento a contagem para no último dia do cultivo. */
    public static GrowAge of(LocalDate startedOn, LocalDate endedOn, LocalDate date) {
        LocalDate reference = endedOn != null && date.isAfter(endedOn) ? endedOn : date;
        return fromElapsedDays(ChronoUnit.DAYS.between(startedOn, reference));
    }
}
