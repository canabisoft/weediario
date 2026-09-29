package br.com.weediario.social.infrastructure.diary;

import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.domain.model.CommentableDiary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DiaryAudienceAdapter implements DiaryAudience {

    private final GrowDiaryRepository diaries;

    public DiaryAudienceAdapter(GrowDiaryRepository diaries) {
        this.diaries = diaries;
    }

    @Override
    public Optional<CommentableDiary> find(UUID diaryId) {
        return diaries.findById(new GrowDiaryId(diaryId)).map(diary -> new CommentableDiary(
                diary.id().value(),
                diary.ownerId().value(),
                diary.visibility() == Visibility.PUBLIC));
    }
}
