package br.com.weediario.moderation.presentation.rest;

import br.com.weediario.moderation.application.usecase.AdminMetricsUseCase;
import br.com.weediario.moderation.application.usecase.AdminMetricsUseCase.Dashboard;
import br.com.weediario.moderation.application.usecase.ManageTeamUseCase;
import br.com.weediario.moderation.application.usecase.ManageTeamUseCase.TeamMember;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Painel do admin: métricas agregadas e gestão da equipe. A rota exige o papel ADMIN e os casos de uso conferem de
 * novo (403 para qualquer outro papel).
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminMetricsUseCase metrics;
    private final ManageTeamUseCase team;

    public AdminController(AdminMetricsUseCase metrics, ManageTeamUseCase team) {
        this.metrics = metrics;
        this.team = team;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Dashboard> metrics(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(metrics.dashboard(Viewer.isAdmin(principal)));
    }

    @GetMapping("/team")
    public List<TeamMember> team(@AuthenticationPrincipal Jwt principal) {
        return team.team(Viewer.isAdmin(principal));
    }

    @PostMapping("/team/{username}/promote")
    public TeamMember promote(@AuthenticationPrincipal Jwt principal, @PathVariable @Size(max = 30) String username,
                              @Valid @RequestBody(required = false) NoteRequest request) {
        return team.promote(Viewer.requireId(principal), Viewer.isAdmin(principal), username, noteOf(request));
    }

    @PostMapping("/team/{username}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt principal, @PathVariable @Size(max = 30) String username,
                       @Valid @RequestBody(required = false) NoteRequest request) {
        team.removeFromTeam(Viewer.requireId(principal), Viewer.isAdmin(principal), username, noteOf(request));
    }

    private static String noteOf(NoteRequest request) {
        return request == null ? null : request.note();
    }

    public record NoteRequest(@Size(max = ModerationLogEntry.NOTE_MAX_LENGTH) String note) {
    }
}
