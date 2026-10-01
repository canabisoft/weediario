package br.com.weediario.review.infrastructure.diary;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.ProductSnapshot;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.review.application.port.ReviewedGrows;
import br.com.weediario.review.domain.model.ReviewedGrow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Traduz um {@code GrowDiary} para o {@link ReviewedGrow} de que as reviews precisam.
 */
@Component
public class DiaryReviewedGrows implements ReviewedGrows {

    private final GrowDiaryRepository diaries;

    public DiaryReviewedGrows(GrowDiaryRepository diaries) {
        this.diaries = diaries;
    }

    @Override
    public Optional<ReviewedGrow> find(UUID diaryId) {
        return diaries.findById(new GrowDiaryId(diaryId)).map(diary -> new ReviewedGrow(
                diary.id().value(),
                diary.ownerId().value(),
                diary.status() == DiaryStatus.COMPLETED,
                diary.strain().catalogId(),
                diary.strain().breederId(),
                diary.setup().map(setup -> setup.products().stream().map(ProductSnapshot::id).toList())
                        .orElse(List.of())));
    }
}
