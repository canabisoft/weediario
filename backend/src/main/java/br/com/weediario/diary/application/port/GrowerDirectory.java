package br.com.weediario.diary.application.port;

import br.com.weediario.diary.domain.model.GrowerId;

/**
 * Consulta ao contexto de identidade (anti-corruption layer): o contexto de diários só precisa saber
 * se o grower pode publicar, não como o usuário é modelado.
 */
public interface GrowerDirectory {

    boolean isActiveGrower(GrowerId growerId);
}
