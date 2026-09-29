package br.com.weediario.social.domain.model;

import br.com.weediario.shared.domain.Guard;

/**
 * Identificador de quem reagiu ou denunciou, derivado com chave secreta a partir do usuário <em>e</em> do alvo.
 * <p>
 * Serve só para garantir "uma reação por pessoa". Não é reversível para o id do usuário e é diferente em cada
 * diário/comentário, então nem com acesso ao banco dá para montar o histórico de interações de alguém.
 */
public record ReactorPseudonym(String value) {

    public ReactorPseudonym {
        Guard.notBlank(value, "Pseudônimo");
    }

    @Override
    public String toString() {
        return "ReactorPseudonym[***]";
    }
}
