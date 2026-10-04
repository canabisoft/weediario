package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;
import br.com.weediario.moderation.application.port.ReporterPseudonyms;
import br.com.weediario.moderation.application.port.ReporterStandings;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ReporterStanding;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ActionQuota;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Denúncia feita pela comunidade. Quem denuncia é guardado só como pseudônimo (diferente por conteúdo), nunca
 * aparece para ninguém, nem para a moderação. Com {@link #HIDE_AFTER_REPORTS} denúncias distintas de contas com
 * histórico ({@link ReporterStanding}), o conteúdo sai do público e espera a decisão da moderação. Denúncia de conta
 * nova entra na fila do mesmo jeito, mas não oculta nada: assim, contas criadas em série não censuram ninguém.
 * Cada conta faz no máximo {@link #REPORTS_PER_DAY} denúncias por dia.
 */
public final class ReportContentUseCase {

    public static final int HIDE_AFTER_REPORTS = 3;
    public static final int REPORTS_PER_DAY = 10;
    static final ActionQuota.Limit DAILY_REPORTS = new ActionQuota.Limit("report", REPORTS_PER_DAY, Duration.ofDays(1));

    private final ModerationRepository repository;
    private final ModeratedContent content;
    private final ReporterPseudonyms pseudonyms;
    private final ModeratedAccounts accounts;
    private final ReporterStandings standings;
    private final ActionQuota quota;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ReportContentUseCase(ModerationRepository repository, ModeratedContent content, ReporterPseudonyms pseudonyms,
                                ModeratedAccounts accounts, ReporterStandings standings, ActionQuota quota,
                                UnitOfWork unitOfWork, Clock clock) {
        this.accounts = accounts;
        this.standings = standings;
        this.quota = quota;
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
            quota.consume(reporterId, DAILY_REPORTS);
            Instant now = clock.instant();
            boolean counts = standings.of(reporterId).map(standing -> standing.countsTowardHiding(now)).orElse(false);
            long reports = repository.addReport(ref, pseudonyms.of(reporterId, ref), reason, counts, now);
            if (counts && reports >= HIDE_AFTER_REPORTS && !repository.isHeld(ref)) {
                content.hide(ref);
                repository.hold(ref, HoldCause.REPORTS, null, now);
                return true;
            }
            return false;
        });
    }
}
