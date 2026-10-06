package br.com.weediario.moderation.presentation.rest;

import br.com.weediario.moderation.application.usecase.ReportContentUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals.Signal;
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
    private final SecuritySignals signals;

    public ReportController(ReportContentUseCase reports, SecuritySignals signals) {
        this.reports = reports;
        this.signals = signals;
    }

    @PostMapping("/api/v1/reports")
    public Map<String, Boolean> report(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody ReportRequest request) {
        return counted(reports.report(Viewer.requireId(principal), request.targetType(), request.targetId(),
                request.reason()));
    }

    @PostMapping("/api/v1/reports/profiles/{username}")
    public Map<String, Boolean> reportProfile(@AuthenticationPrincipal Jwt principal,
                                              @PathVariable @Size(max = 30) String username,
                                              @Valid @RequestBody ReasonRequest request) {
        return counted(reports.reportProfile(Viewer.requireId(principal), username, request.reason()));
    }

    private Map<String, Boolean> counted(boolean hidden) {
        signals.record(Signal.REPORTS);
        if (hidden) {
            signals.record(Signal.AUTO_HIDDEN);
        }
        return Map.of("hidden", hidden);
    }

    public record ReasonRequest(@NotNull ReportReason reason) {
    }

    public record ReportRequest(@NotNull TargetType targetType, @NotNull UUID targetId, @NotNull ReportReason reason) {
    }
}
