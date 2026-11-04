package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.HoldState;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.ModerationAppeals;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * O autor de um conteúdo retido manda a sua versão à moderação (Termos de Uso, cláusula 9): uma vez, em até
 * {@link ModerationAppeals#WINDOW} depois de o conteúdo sair do ar. A manifestação aparece na fila junto do conteúdo e
 * some com a retenção quando a moderação decide.
 * <p>
 * Conteúdo que não está retido, ou que não é de quem pede, responde "não encontrado": ninguém descobre o que está na
 * fila da moderação por aqui.
 */
public final class AppealHoldUseCase {

    private static final int TEXT_MIN_LENGTH = 10;

    private final ModerationRepository repository;
    private final ModeratedContent content;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AppealHoldUseCase(ModerationRepository repository, ModeratedContent content, UnitOfWork unitOfWork,
                             Clock clock) {
        this.repository = repository;
        this.content = content;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void execute(UUID authorId, TargetType type, UUID targetId, String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.length() < TEXT_MIN_LENGTH) {
            throw new DomainException("Conte em pelo menos " + TEXT_MIN_LENGTH + " caracteres o que aconteceu");
        }
        if (text.length() > ModerationAppeals.TEXT_MAX_LENGTH) {
            throw new DomainException("A manifestação pode ter até " + ModerationAppeals.TEXT_MAX_LENGTH + " caracteres");
        }
        ContentRef ref = new ContentRef(type, targetId);
        unitOfWork.run(() -> {
            HoldState hold = repository.holdState(ref)
                    .filter(found -> content.find(ref).map(info -> authorId.equals(info.authorId())).orElse(false))
                    .orElseThrow(() -> new ResourceNotFoundException("Conteúdo retido"));
            Instant now = clock.instant();
            if (hold.appealed()) {
                throw new DomainException("Você já mandou a sua versão. A moderação vai ler antes de decidir.");
            }
            if (now.isAfter(hold.appealDeadline())) {
                throw new DomainException("O prazo de 5 dias para se manifestar terminou");
            }
            repository.appeal(ref, text, now);
        });
    }
}
