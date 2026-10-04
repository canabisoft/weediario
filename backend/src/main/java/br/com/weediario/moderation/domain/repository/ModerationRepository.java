package br.com.weediario.moderation.domain.repository;

import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.HoldCause;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ReportReason;

import java.time.Instant;

public interface ModerationRepository {

    /**
     * Registra a denúncia (uma por pseudônimo de quem denuncia; repetir não conta de novo).
     *
     * @param countsTowardHide se a denúncia conta para a ocultação automática (conta com histórico)
     * @return total de denúncias distintas do conteúdo que contam para a ocultação
     */
    long addReport(ContentRef target, String reporterPseudonym, ReportReason reason, boolean countsTowardHide,
                   Instant at);

    void clearReports(ContentRef target);

    boolean isHeld(ContentRef target);

    /** Coloca (ou mantém) o conteúdo na fila "aguardando moderação". */
    void hold(ContentRef target, HoldCause cause, String detail, Instant at);

    void release(ContentRef target);

    void log(ModerationLogEntry entry);

    /** Retenção do log de moderação. */
    int deleteLogBefore(Instant cutoff);

    /** Denúncias e retenções de conteúdo que já não existe (apagado pelo autor, conta excluída...). */
    int deleteOrphans();
}
