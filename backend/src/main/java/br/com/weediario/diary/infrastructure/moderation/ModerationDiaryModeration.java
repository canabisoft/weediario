package br.com.weediario.diary.infrastructure.moderation;

import br.com.weediario.diary.application.port.DiaryModeration;
import br.com.weediario.moderation.application.usecase.ContentHoldUseCase;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ModerationDiaryModeration implements DiaryModeration {

    private final ContentHoldUseCase holds;

    public ModerationDiaryModeration(ContentHoldUseCase holds) {
        this.holds = holds;
    }

    @Override
    public void heldByFilter(UUID diaryId, String reason) {
        holds.heldByFilter(TargetType.DIARY, diaryId, reason);
    }
}
