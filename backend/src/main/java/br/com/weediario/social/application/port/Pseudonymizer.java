package br.com.weediario.social.application.port;

import br.com.weediario.social.domain.model.ReactorPseudonym;

import java.util.UUID;

/**
 * Deriva um pseudônimo estável para o par (usuário, alvo). Mesmo usuário em alvos diferentes gera
 * pseudônimos diferentes e não relacionáveis.
 */
public interface Pseudonymizer {

    ReactorPseudonym pseudonymOf(UUID userId, UUID target);
}
