package br.com.weediario.social.presentation.rest;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import br.com.weediario.social.application.query.GrowerDirectory.FollowedGrower;
import br.com.weediario.social.application.query.GrowerDirectory.GrowerProfile;
import br.com.weediario.social.application.query.GrowerDirectory.RankedGrower;
import br.com.weediario.social.application.query.GrowerDirectory.RankingSort;
import br.com.weediario.social.application.usecase.GrowersUseCase;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GrowersController {

    private final GrowersUseCase growers;

    public GrowersController(GrowersUseCase growers) {
        this.growers = growers;
    }

    @GetMapping("/api/v1/growers/ranking")
    public PageResult<RankedGrower> ranking(@RequestParam(required = false) RankingSort sort,
                                            @RequestParam(required = false) @Size(max = 30) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return growers.ranking(sort, q, new PageQuery(page, size));
    }

    @GetMapping("/api/v1/growers/{username}")
    public GrowerProfile profile(@AuthenticationPrincipal Jwt principal, @PathVariable String username) {
        return growers.profile(username, Viewer.idOf(principal));
    }

    @PutMapping("/api/v1/growers/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal Jwt principal, @PathVariable String username) {
        growers.follow(Viewer.requireId(principal), username);
    }

    @DeleteMapping("/api/v1/growers/{username}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt principal, @PathVariable String username) {
        growers.unfollow(Viewer.requireId(principal), username);
    }

    @GetMapping("/api/v1/me/following")
    public List<FollowedGrower> following(@AuthenticationPrincipal Jwt principal) {
        return growers.following(Viewer.requireId(principal));
    }
}
