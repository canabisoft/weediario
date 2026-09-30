package br.com.weediario.diary.application.query;

import br.com.weediario.diary.application.query.DiaryReadModel.FeedFilter;
import br.com.weediario.diary.application.query.DiaryReadModel.PublicSetup;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BrowseDiariesUseCase {

    public static final int PUBLIC_SETUPS = 12;

    private final DiaryReadModel readModel;
    private final Clock clock;

    public BrowseDiariesUseCase(DiaryReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    /** Feed da comunidade: apenas diários públicos de contas ativas. */
    public PageResult<DiaryCard> feed(FeedFilter filter, Optional<UUID> viewerId, PageQuery page) {
        if (filter.following() && viewerId.isEmpty()) {
            throw new DomainException("Entre na sua conta para ver os diários de quem você segue");
        }
        LocalDate today = LocalDate.now(clock);
        return readModel.publicFeed(filter, viewerId, page).map(summary -> DiaryCard.of(summary, today));
    }

    /** "Meus diários": inclui os privados. */
    public PageResult<DiaryCard> mine(UUID ownerId, PageQuery page) {
        LocalDate today = LocalDate.now(clock);
        return readModel.ownedBy(ownerId, page).map(summary -> DiaryCard.of(summary, today));
    }

    /** Setups mostrados no perfil público: só os que já aparecem em algum diário público. */
    public List<PublicSetup> publicSetups(String username) {
        return readModel.publicSetupsOf(username, PUBLIC_SETUPS);
    }

    public DiaryCard detail(UUID diaryId, Optional<UUID> viewerId) {
        return readModel.findVisible(diaryId, viewerId)
                .map(summary -> DiaryCard.of(summary, LocalDate.now(clock)))
                .orElseThrow(() -> new ResourceNotFoundException("Diário"));
    }
}
