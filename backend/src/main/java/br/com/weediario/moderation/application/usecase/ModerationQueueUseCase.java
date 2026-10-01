package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;
import br.com.weediario.moderation.application.query.ModerationQueries;
import br.com.weediario.moderation.application.query.ModerationQueries.Counts;
import br.com.weediario.moderation.application.query.ModerationQueries.LogRow;
import br.com.weediario.moderation.application.query.ModerationQueries.Pending;
import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O que a moderação vê: a fila (retidos primeiro, depois os mais denunciados) e, só para admin, o log para
 * atender ordem judicial ou responder a questionamentos.
 */
public final class ModerationQueueUseCase {

    private static final int PREVIEW_MAX = 1000;
    private static final Duration MAX_LOG_PERIOD = Duration.ofDays(366 * 5 + 2);

    private final ModerationQueries queries;
    private final ModeratedContent content;
    private final ModeratedAccounts accounts;
    private final Clock clock;

    public ModerationQueueUseCase(ModerationQueries queries, ModeratedContent content, ModeratedAccounts accounts,
                                  Clock clock) {
        this.queries = queries;
        this.content = content;
        this.accounts = accounts;
        this.clock = clock;
    }

    public PageResult<QueueItem> queue(PageQuery page, boolean moderator) {
        requireModerator(moderator);
        return queries.pending(page).map(this::describe);
    }

    public Counts counts(boolean moderator) {
        requireModerator(moderator);
        return queries.counts();
    }

    /** Log de moderação de uma conta (ou de todas) no período. Só administradores. */
    public List<LogRow> log(boolean admin, String username, Instant from, Instant to) {
        if (!admin) {
            throw new ModeratorOnlyException();
        }
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofDays(365)) : from;
        if (start.isAfter(end) || Duration.between(start, end).compareTo(MAX_LOG_PERIOD) > 0) {
            throw new DomainException("Período inválido");
        }
        UUID userId = username == null || username.isBlank() ? null
                : accounts.findByUsername(username.strip()).map(ModeratedAccounts.Account::id)
                        .orElseThrow(() -> new ResourceNotFoundException("Grower"));
        return queries.log(userId, start, end);
    }

    private QueueItem describe(Pending pending) {
        Optional<ContentInfo> info = content.find(pending.ref());
        String text = info.map(ContentInfo::text).orElse(null);
        return new QueueItem(pending.ref().type(), pending.ref().id(), info.isPresent(),
                info.map(ContentInfo::author).orElse(null), info.map(ContentInfo::authorStatus).orElse(null),
                info.map(ContentInfo::title).orElse(null),
                text != null && text.length() > PREVIEW_MAX ? text.substring(0, PREVIEW_MAX) + "…" : text,
                info.map(ContentInfo::linkId).orElse(null),
                pending.cause() != null, pending.cause(), pending.detail(), pending.reports(),
                queries.reasons(pending.ref()), pending.since());
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }

    /**
     * Item da fila. Nunca traz quem denunciou: só quantas denúncias e por qual motivo.
     *
     * @param exists falso se o conteúdo já foi apagado pelo autor (sai da fila na próxima limpeza)
     * @param held   fora do público, aguardando decisão
     */
    public record QueueItem(TargetType type, UUID id, boolean exists, String author, String authorStatus, String title,
                            String text, String linkId, boolean held, HoldCause cause, String detail, long reports,
                            Map<ReportReason, Long> reasons, Instant since) {
    }
}
