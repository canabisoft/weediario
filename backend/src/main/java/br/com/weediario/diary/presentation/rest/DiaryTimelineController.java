package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.DiaryUpdateView;
import br.com.weediario.diary.application.port.PhotoLibrary.PhotoSize;
import br.com.weediario.diary.application.usecase.DeleteDiaryUpdateUseCase;
import br.com.weediario.diary.application.usecase.GetDiaryPhotoUseCase;
import br.com.weediario.diary.application.usecase.ListDiaryUpdatesUseCase;
import br.com.weediario.diary.application.usecase.PublishDiaryUpdateUseCase;
import br.com.weediario.diary.presentation.rest.dto.PublishDiaryUpdateRequest;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diaries/{diaryId}")
public class DiaryTimelineController {

    private final PublishDiaryUpdateUseCase publishUpdate;
    private final DeleteDiaryUpdateUseCase deleteUpdate;
    private final ListDiaryUpdatesUseCase listUpdates;
    private final GetDiaryPhotoUseCase getPhoto;

    public DiaryTimelineController(PublishDiaryUpdateUseCase publishUpdate, DeleteDiaryUpdateUseCase deleteUpdate,
                                   ListDiaryUpdatesUseCase listUpdates, GetDiaryPhotoUseCase getPhoto) {
        this.publishUpdate = publishUpdate;
        this.deleteUpdate = deleteUpdate;
        this.listUpdates = listUpdates;
        this.getPhoto = getPhoto;
    }

    @PostMapping("/updates")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryUpdateView publish(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                   @Valid @RequestBody PublishDiaryUpdateRequest request) {
        return publishUpdate.execute(new PublishDiaryUpdateUseCase.Command(
                diaryId,
                Viewer.requireId(principal),
                request.stage(),
                request.recordedOn(),
                request.note(),
                request.temperatureCelsius(),
                request.relativeHumidity(),
                request.wateringPh(),
                request.plantHeightCm(),
                request.photoIds() == null ? List.of() : request.photoIds()));
    }

    @GetMapping("/updates")
    public PageResult<DiaryUpdateView> timeline(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return listUpdates.execute(diaryId, Viewer.idOf(principal), new PageQuery(page, size));
    }

    @DeleteMapping("/updates/{updateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId, @PathVariable UUID updateId) {
        deleteUpdate.execute(diaryId, updateId, Viewer.requireId(principal));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                        @PathVariable UUID photoId,
                                        @RequestParam(defaultValue = "FULL") PhotoSize size) {
        GetDiaryPhotoUseCase.Photo photo = getPhoto.execute(diaryId, photoId, size, Viewer.idOf(principal));
        // Fotos de diários privados nunca vão para cache compartilhado (CDN/proxy).
        // O conteúdo de um id nunca muda, então o navegador não precisa revalidar ("immutable").
        CacheControl cache = photo.publiclyCacheable()
                ? CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable()
                : CacheControl.noStore().cachePrivate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.mediaType()))
                .cacheControl(cache)
                .body(photo.content());
    }
}
