package br.com.weediario.moderation.application.port;

import br.com.weediario.moderation.domain.model.ContentRef;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso da moderação ao conteúdo dos outros contextos: descrever, ocultar, restaurar e remover.
 * Ocultar nunca apaga; remover segue a regra de cada tipo (comentário e review são apagados; diário, receita e
 * perfil ficam fora do público de vez).
 */
public interface ModeratedContent {

    Optional<ContentInfo> find(ContentRef ref);

    void hide(ContentRef ref);

    void restore(ContentRef ref);

    void remove(ContentRef ref);

    /**
     * @param authorId       quem publicou (responde pelo conteúdo)
     * @param containerOwner dono do diário/receita onde o comentário está (pode ver mesmo se privado)
     * @param publiclyVisible se o público consegue ver hoje (critério para aceitar denúncia)
     * @param title          contexto curto para a moderação (título do diário, nome da strain...)
     * @param text           texto do conteúdo (prévia na fila e hash no log)
     * @param linkId         id para abrir no app (diário, receita, strain) ou nulo
     */
    record ContentInfo(ContentRef ref, UUID authorId, String author, String authorStatus, UUID containerOwner,
                       boolean publiclyVisible, String title, String text, String linkId) {

        public boolean visibleTo(UUID viewerId) {
            return publiclyVisible || viewerId.equals(containerOwner);
        }
    }
}
