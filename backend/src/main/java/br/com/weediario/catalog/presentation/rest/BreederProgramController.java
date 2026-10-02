package br.com.weediario.catalog.presentation.rest;

import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.BrowseCatalogUseCase.BreederProgram;
import br.com.weediario.catalog.application.query.CatalogQueries.RepresentativeRequest;
import br.com.weediario.catalog.application.usecase.BreederProgramUseCase;
import br.com.weediario.catalog.domain.model.BreederRepresentative;
import br.com.weediario.catalog.domain.model.CatalogEnums.BreederKind;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.presentation.rest.CatalogController.TraitsRequest;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Programa de breeders: pedir para representar um breeder, cadastrar sementes para teste e, na moderação,
 * aprovar ou revogar representantes. Tudo exige login; quem representa um breeder nunca aparece em rota pública.
 */
@RestController
public class BreederProgramController {

    private final BrowseCatalogUseCase browse;
    private final BreederProgramUseCase program;

    public BreederProgramController(BrowseCatalogUseCase browse, BreederProgramUseCase program) {
        this.browse = browse;
        this.program = program;
    }

    /** Pedidos de representação da própria pessoa e as sementes dos breeders que ela representa. */
    @GetMapping("/api/v1/breeder-program")
    public BreederProgram mine(@AuthenticationPrincipal Jwt principal) {
        return browse.program(Viewer.requireId(principal));
    }

    @PostMapping("/api/v1/breeder-program/representations")
    @ResponseStatus(HttpStatus.CREATED)
    public void request(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody RepresentationRequest request) {
        program.requestRepresentation(Viewer.requireId(principal), request.breederId(), request.note(),
                request.kind(), Boolean.TRUE.equals(request.publicCredit()));
    }

    /** Breeder independente decide se aparece como criador(a) das genéticas. */
    @PutMapping("/api/v1/breeder-program/representations/{breederId}/credit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void credit(@AuthenticationPrincipal Jwt principal, @PathVariable UUID breederId,
                       @Valid @RequestBody CreditRequest request) {
        program.changePublicCredit(Viewer.requireId(principal), breederId, request.publicCredit());
    }

    @DeleteMapping("/api/v1/breeder-program/representations/{breederId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Jwt principal, @PathVariable UUID breederId) {
        program.withdraw(Viewer.requireId(principal), breederId);
    }

    /** Semente nova do breeder, que entra "em teste" até a comunidade cultivar e avaliar. */
    @PostMapping("/api/v1/breeder-program/strains")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItem registerStrain(@AuthenticationPrincipal Jwt principal,
                                      @Valid @RequestBody BreederStrainRequest request) {
        return program.registerStrain(Viewer.requireId(principal), request.breederId(), request.name(),
                request.traits() == null ? null : request.traits().traits());
    }

    // ------------------------------------------------------------------------------------- moderação

    @GetMapping("/api/v1/moderation/breeder-representatives")
    public PageResult<RepresentativeRequest> requests(@AuthenticationPrincipal Jwt principal,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return browse.representativeRequests(new PageQuery(page, size), Viewer.isModerator(principal));
    }

    @PostMapping("/api/v1/moderation/breeder-representatives/{breederId}/{userId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal Jwt principal, @PathVariable UUID breederId, @PathVariable UUID userId) {
        program.approve(breederId, userId, Viewer.requireId(principal), Viewer.isModerator(principal));
    }

    /** Recusa o pedido ou revoga o representante. */
    @DeleteMapping("/api/v1/moderation/breeder-representatives/{breederId}/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Jwt principal, @PathVariable UUID breederId, @PathVariable UUID userId) {
        program.reject(breederId, userId, Viewer.requireId(principal), Viewer.isModerator(principal));
    }

    /**
     * @param kind         o que a pessoa representa (padrão: banco de sementes)
     * @param publicCredit só para breeder independente: aparecer como criador(a)
     */
    public record RepresentationRequest(
            @NotNull UUID breederId,
            @NotBlank @Size(min = BreederRepresentative.NOTE_MIN_LENGTH, max = BreederRepresentative.NOTE_MAX_LENGTH)
            String note,
            BreederKind kind,
            Boolean publicCredit) {
    }

    public record CreditRequest(boolean publicCredit) {
    }

    public record BreederStrainRequest(
            @NotNull UUID breederId,
            @NotBlank @Size(max = CatalogStrain.NAME_MAX_LENGTH) String name,
            @Valid TraitsRequest traits) {
    }
}
