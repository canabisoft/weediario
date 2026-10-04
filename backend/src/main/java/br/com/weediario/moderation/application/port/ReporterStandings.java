package br.com.weediario.moderation.application.port;

import br.com.weediario.moderation.domain.model.ReporterStanding;

import java.util.Optional;
import java.util.UUID;

/**
 * Fatos da conta de quem denuncia (idade, e-mail confirmado, updates publicados), lidos de outros contextos.
 */
public interface ReporterStandings {

    Optional<ReporterStanding> of(UUID accountId);
}
