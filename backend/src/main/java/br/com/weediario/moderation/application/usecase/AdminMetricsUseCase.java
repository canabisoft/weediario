package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.query.ModerationQueries;
import br.com.weediario.moderation.application.query.PlatformMetrics;
import br.com.weediario.moderation.application.query.PlatformMetrics.Accounts;
import br.com.weediario.moderation.application.query.PlatformMetrics.Activity;
import br.com.weediario.moderation.application.query.PlatformMetrics.Content;
import br.com.weediario.shared.application.ForbiddenActionException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Painel do admin: números da plataforma, só agregados (ver {@link PlatformMetrics}). */
public final class AdminMetricsUseCase {

    private final PlatformMetrics metrics;
    private final ModerationQueries moderation;
    private final Clock clock;

    public AdminMetricsUseCase(PlatformMetrics metrics, ModerationQueries moderation, Clock clock) {
        this.metrics = metrics;
        this.moderation = moderation;
        this.clock = clock;
    }

    public Dashboard dashboard(boolean admin) {
        if (!admin) {
            throw new ForbiddenActionException("Só admins veem as métricas da plataforma");
        }
        Instant now = clock.instant();
        Instant monthAgo = now.minus(Duration.ofDays(30));
        Content content = metrics.content();
        ModerationQueries.Counts queue = moderation.counts();
        return new Dashboard(now, metrics.accounts(), content, content.published(),
                metrics.activitySince(now.minus(Duration.ofDays(7))), metrics.activitySince(monthAgo),
                new Moderation(queue.pending(), queue.held(), metrics.moderationDecisionsSince(monthAgo)));
    }

    /** @param published total de conteúdo visível ao público */
    public record Dashboard(Instant generatedAt, Accounts accounts, Content content, long published,
                            Activity last7Days, Activity last30Days, Moderation moderation) {
    }

    /**
     * @param pending           itens na fila (denunciados ou retidos)
     * @param held              retidos pelo filtro anti-comércio
     * @param decisionsLast30Days entradas no log de moderação nos últimos 30 dias
     */
    public record Moderation(long pending, long held, long decisionsLast30Days) {
    }
}
