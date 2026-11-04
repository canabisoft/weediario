package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.HoldNotifier;
import br.com.weediario.moderation.application.port.ModeratedContent;
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
    private final ModeratedContent content;
    private final HoldNotifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ContentHoldUseCase(ModerationRepository repository, ModeratedContent content, HoldNotifier notifier,
                              UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.content = content;
        this.notifier = notifier;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Na primeira retenção o autor é avisado e pode mandar a sua versão (Termos, cláusula 9). */
    public void heldByFilter(TargetType type, UUID targetId, String reason) {
        ContentRef ref = new ContentRef(type, targetId);
        unitOfWork.run(() -> {
            if (repository.hold(ref, HoldCause.AUTO_FILTER, reason, clock.instant())) {
                content.find(ref).ifPresent(notifier::contentHeld);
            }
        });
    }
}
