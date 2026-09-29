package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.GrowSetupView;
import br.com.weediario.diary.application.usecase.ManageGrowSetupsUseCase;
import br.com.weediario.diary.application.usecase.ManageGrowSetupsUseCase.Details;
import br.com.weediario.diary.presentation.rest.dto.GrowSetupRequest;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Setups do grower logado. Não há rota pública: setups só aparecem para terceiros como cópia dentro dos diários.
 */
@RestController
@RequestMapping("/api/v1/me/setups")
public class GrowSetupController {

    private final ManageGrowSetupsUseCase setups;

    public GrowSetupController(ManageGrowSetupsUseCase setups) {
        this.setups = setups;
    }

    @GetMapping
    public List<GrowSetupView> list(@AuthenticationPrincipal Jwt principal) {
        return setups.list(Viewer.requireId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrowSetupView create(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody GrowSetupRequest request) {
        return setups.create(Viewer.requireId(principal), details(request));
    }

    @PutMapping("/{setupId}")
    public GrowSetupView revise(@AuthenticationPrincipal Jwt principal, @PathVariable UUID setupId,
                                @Valid @RequestBody GrowSetupRequest request) {
        return setups.revise(Viewer.requireId(principal), setupId, details(request));
    }

    @DeleteMapping("/{setupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID setupId) {
        setups.delete(Viewer.requireId(principal), setupId);
    }

    private static Details details(GrowSetupRequest request) {
        return new Details(request.name(), request.environment(), request.spec());
    }
}
