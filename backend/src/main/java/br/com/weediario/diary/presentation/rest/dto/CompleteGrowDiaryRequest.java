package br.com.weediario.diary.presentation.rest.dto;

import java.time.LocalDate;

/**
 * @param harvestedOn data da colheita; hoje, se omitida
 */
public record CompleteGrowDiaryRequest(LocalDate harvestedOn) {
}
