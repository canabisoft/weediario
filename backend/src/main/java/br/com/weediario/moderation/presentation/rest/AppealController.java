package br.com.weediario.moderation.presentation.rest;

import br.com.weediario.moderation.application.usecase.AppealHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.domain.ModerationAppeals;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** O autor de um conteúdo retido manda a sua versão à moderação (Termos de Uso, cláusula 9). */
@RestController
public class AppealController {

    private final AppealHoldUseCase appeals;

    public AppealController(AppealHoldUseCase appeals) {
        this.appeals = appeals;
    }

    @PostMapping("/api/v1/me/moderation/appeals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void appeal(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody AppealRequest request) {
        appeals.execute(Viewer.requireId(principal), request.targetType(), request.targetId(), request.text());
    }

    public record AppealRequest(@NotNull TargetType targetType, @NotNull UUID targetId,
                                @NotNull @Size(max = ModerationAppeals.TEXT_MAX_LENGTH) String text) {
    }
}
