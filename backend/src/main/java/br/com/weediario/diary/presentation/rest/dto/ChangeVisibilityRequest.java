package br.com.weediario.diary.presentation.rest.dto;

import br.com.weediario.diary.domain.model.Visibility;
import jakarta.validation.constraints.NotNull;

public record ChangeVisibilityRequest(@NotNull Visibility visibility) {
}
