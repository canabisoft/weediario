package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.application.query.BrowseDiariesUseCase;
import br.com.weediario.diary.application.query.DiaryCard;
import br.com.weediario.diary.application.usecase.ChangeDiaryVisibilityUseCase;
import br.com.weediario.diary.application.usecase.CreateGrowDiaryUseCase;
import br.com.weediario.diary.application.usecase.FinishGrowDiaryUseCase;
import br.com.weediario.diary.presentation.rest.dto.ChangeVisibilityRequest;
import br.com.weediario.diary.presentation.rest.dto.CompleteGrowDiaryRequest;
import br.com.weediario.diary.presentation.rest.dto.CreateGrowDiaryRequest;
import br.com.weediario.diary.presentation.rest.dto.RecordHarvestRequest;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diaries")
public class GrowDiaryController {

    private final CreateGrowDiaryUseCase createGrowDiary;
    private final FinishGrowDiaryUseCase finishGrowDiary;
    private final ChangeDiaryVisibilityUseCase changeVisibility;
    private final BrowseDiariesUseCase browseDiaries;

    public GrowDiaryController(CreateGrowDiaryUseCase createGrowDiary, FinishGrowDiaryUseCase finishGrowDiary,
                               ChangeDiaryVisibilityUseCase changeVisibility, BrowseDiariesUseCase browseDiaries) {
        this.createGrowDiary = createGrowDiary;
        this.finishGrowDiary = finishGrowDiary;
        this.changeVisibility = changeVisibility;
        this.browseDiaries = browseDiaries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrowDiaryView create(@AuthenticationPrincipal Jwt principal,
                                @Valid @RequestBody CreateGrowDiaryRequest request) {
        return createGrowDiary.execute(new CreateGrowDiaryUseCase.Command(
                Viewer.requireId(principal),
                request.title(),
                request.strainName(),
                request.breeder(),
                request.floweringType(),
                request.seedSex(),
                request.medium(),
                request.visibility(),
                request.startedOn(),
                request.setupId(),
                request.strainId()));
    }

    @GetMapping("/{diaryId}")
    public DiaryCard detail(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId) {
        return browseDiaries.detail(diaryId, Viewer.idOf(principal));
    }

    @PostMapping("/{diaryId}/complete")
    public GrowDiaryView complete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                  @Valid @RequestBody(required = false) CompleteGrowDiaryRequest request) {
        return finishGrowDiary.complete(diaryId, Viewer.requireId(principal),
                request == null ? null : request.harvestedOn(), request == null ? null : request.harvest());
    }

    @PutMapping("/{diaryId}/harvest")
    public GrowDiaryView recordHarvest(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                       @Valid @RequestBody RecordHarvestRequest request) {
        return finishGrowDiary.recordHarvest(diaryId, Viewer.requireId(principal), request.harvest());
    }

    @PostMapping("/{diaryId}/cancel")
    public GrowDiaryView cancel(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId) {
        return finishGrowDiary.cancel(diaryId, Viewer.requireId(principal));
    }

    @PutMapping("/{diaryId}/visibility")
    public GrowDiaryView changeVisibility(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                          @Valid @RequestBody ChangeVisibilityRequest request) {
        return changeVisibility.execute(diaryId, Viewer.requireId(principal), request.visibility());
    }
}
