package br.com.weediario.shared.presentation;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Extrai o id de quem está vendo a página. Vazio para visitantes não autenticados.
 */
public final class Viewer {

    private Viewer() {
    }

    public static Optional<UUID> idOf(Jwt principal) {
        return Optional.ofNullable(principal).map(jwt -> UUID.fromString(jwt.getSubject()));
    }

    public static UUID requireId(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
