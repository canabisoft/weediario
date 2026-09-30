package br.com.weediario.catalog.presentation.rest;

import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.CatalogQueries.PendingItem;
import br.com.weediario.catalog.application.query.CatalogQueries.RankingKind;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fila da moderação do catálogo. As ações (verificar, juntar, recusar) ficam em {@link CatalogController}.
 */
@RestController
@RequestMapping("/api/v1/moderation/catalog")
public class CatalogModerationController {

    private final BrowseCatalogUseCase browse;

    public CatalogModerationController(BrowseCatalogUseCase browse) {
        this.browse = browse;
    }

    @GetMapping("/counts")
    public Map<RankingKind, Long> counts(@AuthenticationPrincipal Jwt principal) {
        return browse.pendingCounts(Viewer.isModerator(principal));
    }

    @GetMapping("/{kind}")
    public PageResult<PendingItem> pending(@AuthenticationPrincipal Jwt principal, @PathVariable String kind,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        RankingKind parsed = RankingKind.valueOf(CatalogController.kindOf(kind).name() + "S");
        return browse.pending(parsed, new PageQuery(page, size), Viewer.isModerator(principal));
    }
}
