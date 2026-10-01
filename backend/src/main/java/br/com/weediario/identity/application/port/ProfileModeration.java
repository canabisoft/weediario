package br.com.weediario.identity.application.port;

import java.util.UUID;

/** Manda para a fila da moderação um perfil que o filtro anti-comércio reteve (a bio já fica oculta). */
public interface ProfileModeration {

    void heldByFilter(UUID userId, String reason);
}
