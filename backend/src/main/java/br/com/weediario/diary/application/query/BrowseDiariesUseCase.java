package br.com.weediario.diary.application.query;

import br.com.weediario.diary.application.query.DiaryReadModel.FeedFilter;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public final class BrowseDiariesUseCase {

    private final DiaryReadModel readModel;
    private final Clock clock;

    public BrowseDiariesUseCase(DiaryReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    /** Feed da comunidade: apenas diários públicos de contas ativas. */
    public PageResult<DiaryCard> feed(FeedFilter filter, PageQuery page) {
        LocalDate today = LocalDate.now(clock);
        return readModel.publicFeed(filter, page).map(summary -> DiaryCard.of(summary, today));
    }

    /** "Meus diários": inclui os privados. */
    public PageResult<DiaryCard> mine(UUID ownerId, PageQuery page) {
        LocalDate today = LocalDate.now(clock);
        return readModel.ownedBy(ownerId, page).map(summary -> DiaryCard.of(summary, today));
    }

    public DiaryCard detail(UUID diaryId, Optional<UUID> viewerId) {
        return readModel.findVisible(diaryId, viewerId)
                .map(summary -> DiaryCard.of(summary, LocalDate.now(clock)))
                .orElseThrow(() -> new ResourceNotFoundException("Diário"));
    }
}
