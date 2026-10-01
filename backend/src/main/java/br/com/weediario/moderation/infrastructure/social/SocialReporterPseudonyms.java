package br.com.weediario.moderation.infrastructure.social;

import br.com.weediario.moderation.application.port.ReporterPseudonyms;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.social.application.port.Pseudonymizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reusa o mesmo pseudônimo HMAC das reações e das denúncias antigas de comentário (mesma chave, mesmo alvo):
 * as denúncias migradas continuam impedindo denúncia repetida.
 */
@Component
public class SocialReporterPseudonyms implements ReporterPseudonyms {

    private final Pseudonymizer pseudonymizer;

    public SocialReporterPseudonyms(Pseudonymizer pseudonymizer) {
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public String of(UUID reporterId, ContentRef target) {
        return pseudonymizer.pseudonymOf(reporterId, target.id()).value();
    }
}
