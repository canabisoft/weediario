package br.com.weediario.moderation.presentation.rest;

import br.com.weediario.moderation.application.usecase.ReportContentUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Denúncia de qualquer conteúdo público (comentário, diário, review, receita, comentário de receita, perfil). */
@RestController
public class ReportController {

    private final ReportContentUseCase reports;

    public ReportController(ReportContentUseCase reports) {
        this.reports = reports;
    }

    @PostMapping("/api/v1/reports")
    public Map<String, Boolean> report(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody ReportRequest request) {
        boolean hidden = reports.report(Viewer.requireId(principal), request.targetType(), request.targetId(),
                request.reason());
        return Map.of("hidden", hidden);
    }

    @PostMapping("/api/v1/reports/profiles/{username}")
    public Map<String, Boolean> reportProfile(@AuthenticationPrincipal Jwt principal,
                                              @PathVariable @Size(max = 30) String username,
                                              @Valid @RequestBody ReasonRequest request) {
        return Map.of("hidden", reports.reportProfile(Viewer.requireId(principal), username, request.reason()));
    }

    public record ReasonRequest(@NotNull ReportReason reason) {
    }

    public record ReportRequest(@NotNull TargetType targetType, @NotNull UUID targetId, @NotNull ReportReason reason) {
    }
}
