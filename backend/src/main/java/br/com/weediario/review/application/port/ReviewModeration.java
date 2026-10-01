package br.com.weediario.review.application.port;

import java.util.UUID;

/** Manda para a fila da moderação uma review que o filtro anti-comércio reteve (já salva oculta). */
public interface ReviewModeration {

    void heldByFilter(UUID reviewId, String reason);
}
