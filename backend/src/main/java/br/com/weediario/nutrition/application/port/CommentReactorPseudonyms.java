package br.com.weediario.nutrition.application.port;

import java.util.UUID;

/**
 * Pseudônimo de quem reage a um comentário de receita, diferente em cada comentário (e diferente do pseudônimo de
 * denúncia do mesmo comentário). Não é reversível para o id do usuário.
 */
public interface CommentReactorPseudonyms {

    String of(UUID userId, UUID commentId);
}
