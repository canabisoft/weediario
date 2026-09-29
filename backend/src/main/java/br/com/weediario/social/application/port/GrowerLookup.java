package br.com.weediario.social.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta ao contexto de identidade: encontra growers ativos pelo apelido público.
 */
public interface GrowerLookup {

    Optional<UUID> activeGrowerId(String username);
}
