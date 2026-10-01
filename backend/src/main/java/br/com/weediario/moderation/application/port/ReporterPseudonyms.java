package br.com.weediario.moderation.application.port;

import br.com.weediario.moderation.domain.model.ContentRef;

import java.util.UUID;

/**
 * Pseudônimo de quem denuncia, diferente para cada conteúdo: impede denúncia repetida sem guardar quem foi.
 */
public interface ReporterPseudonyms {

    String of(UUID reporterId, ContentRef target);
}
