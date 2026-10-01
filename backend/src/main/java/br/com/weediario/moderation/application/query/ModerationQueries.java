package br.com.weediario.moderation.application.query;

import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Leitura da moderação: fila (retidos primeiro), contagens e o log para exportação. */
public interface ModerationQueries {

    PageResult<Pending> pending(PageQuery page);

    Map<ReportReason, Long> reasons(ContentRef ref);

    Counts counts();

    List<LogRow> log(UUID reportedUserId, Instant from, Instant to);

    /**
     * @param cause   por que está retido (nulo: tem denúncias, mas ainda abaixo do limite)
     * @param since   quando foi retido ou recebeu a primeira denúncia
     */
    record Pending(ContentRef ref, HoldCause cause, String detail, long reports, Instant since) {
    }

    record Counts(long pending, long held, Map<TargetType, Long> byType) {
    }

    record LogRow(Instant at, String targetType, UUID targetId, String reportedUsername, String moderator,
                  String action, String reason, String note, String contentSha256) {
    }
}
