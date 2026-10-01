package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.UUID;

/**
 * Entrada dos outros contextos quando o filtro anti-comércio retém um texto: o contexto já salvou o conteúdo
 * fora do público; aqui ele só entra na fila da moderação. Roda na transação de quem publicou.
 */
public final class ContentHoldUseCase {

    private final ModerationRepository repository;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ContentHoldUseCase(ModerationRepository repository, UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void heldByFilter(TargetType type, UUID targetId, String reason) {
        unitOfWork.run(() -> repository.hold(new ContentRef(type, targetId), HoldCause.AUTO_FILTER, reason,
                clock.instant()));
    }
}
