package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.query.BrowseDiariesUseCase;
import br.com.weediario.diary.application.query.DiaryCard;
import br.com.weediario.diary.application.query.DiaryReadModel.FeedFilter;
import br.com.weediario.diary.application.query.DiaryReadModel.FeedSort;
import br.com.weediario.diary.application.query.DiaryReadModel.PublicSetup;
import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.Strain;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DiaryFeedController {

    private final BrowseDiariesUseCase browseDiaries;

    public DiaryFeedController(BrowseDiariesUseCase browseDiaries) {
        this.browseDiaries = browseDiaries;
    }

    /** Aberto a visitantes: só expõe diários públicos, com o autor identificado apenas pelo apelido. */
    @GetMapping("/api/v1/feed/diaries")
    public PageResult<DiaryCard> feed(@RequestParam(required = false) GrowthStage stage,
                                      @RequestParam(required = false) GrowMedium medium,
                                      @RequestParam(required = false) DiaryStatus status,
                                      @RequestParam(required = false) FloweringType floweringType,
                                      @RequestParam(required = false) @Size(max = Strain.NAME_MAX_LENGTH) String strain,
                                      @RequestParam(required = false) FeedSort sort,
                                      @RequestParam(defaultValue = "false") boolean following,
                                      @RequestParam(required = false) @Size(max = 30) String author,
                                      @AuthenticationPrincipal Jwt principal,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return browseDiaries.feed(new FeedFilter(stage, medium, status, floweringType, strain, sort, following, author),
                Viewer.idOf(principal), new PageQuery(page, size));
    }

    @GetMapping("/api/v1/me/diaries")
    public PageResult<DiaryCard> mine(@AuthenticationPrincipal Jwt principal,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return browseDiaries.mine(Viewer.requireId(principal), new PageQuery(page, size));
    }

    /** Perfil público: setups que o grower já usou em diários públicos (nada que não esteja já visível num diário). */
    @GetMapping("/api/v1/growers/{username}/setups")
    public List<PublicSetup> publicSetups(@PathVariable @Size(max = 30) String username) {
        return browseDiaries.publicSetups(username);
    }
}
