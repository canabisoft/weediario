package br.com.weediario.social.application.port;

import java.util.UUID;

/** Manda para a fila da moderação um comentário que o filtro anti-comércio reteve (já salvo oculto). */
public interface CommentModeration {

    void heldByFilter(UUID commentId, String reason);
}
