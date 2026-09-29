package br.com.weediario.social.application.usecase;

import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.social.application.port.DiaryAudience;
import br.com.weediario.social.application.port.Pseudonymizer;
import br.com.weediario.social.domain.model.CommentableDiary;
import br.com.weediario.social.domain.model.ReactionType;
import br.com.weediario.social.domain.repository.ReactionRepository;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reações anônimas: o público (incluindo o dono do diário) vê apenas as contagens.
 * Cada pessoa só enxerga as próprias reações, para poder desfazê-las.
 */
public final class DiaryReactionsUseCase {

    private final DiaryAudience diaries;
    private final ReactionRepository reactions;
    private final Pseudonymizer pseudonymizer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DiaryReactionsUseCase(DiaryAudience diaries, ReactionRepository reactions, Pseudonymizer pseudonymizer,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.diaries = diaries;
        this.reactions = reactions;
        this.pseudonymizer = pseudonymizer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public ReactionSummary summary(UUID diaryId, Optional<UUID> viewerId) {
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, viewerId);
            return summaryOf(diary, viewerId);
        });
    }

    public ReactionSummary react(UUID diaryId, UUID userId, ReactionType type) {
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(userId));
            reactions.add(diary.diaryId(), pseudonymizer.pseudonymOf(userId, diary.diaryId()), type, clock.instant());
            return summaryOf(diary, Optional.of(userId));
        });
    }

    public ReactionSummary unreact(UUID diaryId, UUID userId, ReactionType type) {
        return unitOfWork.execute(() -> {
            CommentableDiary diary = visibleDiary(diaryId, Optional.of(userId));
            reactions.remove(diary.diaryId(), pseudonymizer.pseudonymOf(userId, diary.diaryId()), type);
            return summaryOf(diary, Optional.of(userId));
        });
    }

    private ReactionSummary summaryOf(CommentableDiary diary, Optional<UUID> viewerId) {
        Map<ReactionType, Long> counts = new EnumMap<>(ReactionType.class);
        for (ReactionType type : ReactionType.values()) {
            counts.put(type, 0L);
        }
        counts.putAll(reactions.countByType(diary.diaryId()));
        Set<ReactionType> mine = viewerId
                .map(viewer -> reactions.typesBy(diary.diaryId(), pseudonymizer.pseudonymOf(viewer, diary.diaryId())))
                .orElse(Set.of());
        return new ReactionSummary(counts, mine);
    }

    private CommentableDiary visibleDiary(UUID diaryId, Optional<UUID> viewerId) {
        return diaries.find(diaryId)
                .filter(diary -> diary.isVisibleTo(viewerId))
                .orElseThrow(() -> new ResourceNotFoundException("Diário"));
    }

    public record ReactionSummary(Map<ReactionType, Long> counts, Set<ReactionType> mine) {
    }
}
