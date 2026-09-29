package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.query.BrowseDiariesUseCase;
import br.com.weediario.diary.application.query.DiaryCard;
import br.com.weediario.diary.application.query.DiaryReadModel.FeedFilter;
import br.com.weediario.diary.application.query.DiaryReadModel.FeedSort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return browseDiaries.feed(new FeedFilter(stage, medium, status, floweringType, strain, sort),
                new PageQuery(page, size));
    }

    @GetMapping("/api/v1/me/diaries")
    public PageResult<DiaryCard> mine(@AuthenticationPrincipal Jwt principal,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return browseDiaries.mine(Viewer.requireId(principal), new PageQuery(page, size));
    }
}
