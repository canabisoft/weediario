package br.com.weediario.nutrition.infrastructure.diary;

import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.nutrition.application.port.GrowDiaries;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class GrowDiariesAdapter implements GrowDiaries {

    private final GrowDiaryRepository diaries;

    public GrowDiariesAdapter(GrowDiaryRepository diaries) {
        this.diaries = diaries;
    }

    @Override
    public Optional<DiaryRef> find(UUID diaryId) {
        return diaries.findById(new GrowDiaryId(diaryId)).map(diary ->
                new DiaryRef(diary.id().value(), diary.ownerId().value(), diary.visibility() == Visibility.PUBLIC,
                        diary.medium() != GrowMedium.LIVING_SOIL));
    }
}
