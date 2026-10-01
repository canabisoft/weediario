package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;
import br.com.weediario.moderation.application.port.ReporterPseudonyms;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.util.UUID;

/**
 * Denúncia feita pela comunidade. Quem denuncia é guardado só como pseudônimo (diferente por conteúdo), nunca
 * aparece para ninguém, nem para a moderação. Com {@link #HIDE_AFTER_REPORTS} denúncias distintas, o conteúdo sai
 * do público e espera a decisão da moderação.
 */
public final class ReportContentUseCase {

    public static final int HIDE_AFTER_REPORTS = 3;

    private final ModerationRepository repository;
    private final ModeratedContent content;
    private final ReporterPseudonyms pseudonyms;
    private final ModeratedAccounts accounts;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ReportContentUseCase(ModerationRepository repository, ModeratedContent content, ReporterPseudonyms pseudonyms,
                                ModeratedAccounts accounts, UnitOfWork unitOfWork, Clock clock) {
        this.accounts = accounts;
        this.repository = repository;
        this.content = content;
        this.pseudonyms = pseudonyms;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Perfil é denunciado pelo apelido: a API pública não expõe o id interno das contas. */
    public boolean reportProfile(UUID reporterId, String username, ReportReason reason) {
        UUID userId = accounts.findByUsername(username == null ? "" : username.strip())
                .map(ModeratedAccounts.Account::id)
                .orElseThrow(() -> new ResourceNotFoundException("Grower"));
        return report(reporterId, TargetType.PROFILE, userId, reason);
    }

    /** @return se o conteúdo saiu do público com esta denúncia */
    public boolean report(UUID reporterId, TargetType type, UUID targetId, ReportReason reason) {
        if (reason == null) {
            throw new DomainException("Escolha o motivo da denúncia");
        }
        ContentRef ref = new ContentRef(type, targetId);
        return unitOfWork.execute(() -> {
            ContentInfo info = content.find(ref)
                    .filter(found -> found.visibleTo(reporterId))
                    .orElseThrow(() -> new ResourceNotFoundException("Conteúdo"));
            if (reporterId.equals(info.authorId())) {
                throw new DomainException("Não dá para denunciar o próprio conteúdo");
            }
            long reports = repository.addReport(ref, pseudonyms.of(reporterId, ref), reason, clock.instant());
            if (reports >= HIDE_AFTER_REPORTS && !repository.isHeld(ref)) {
                content.hide(ref);
                repository.hold(ref, HoldCause.REPORTS, null, clock.instant());
                return true;
            }
            return false;
        });
    }
}
