package br.com.weediario.diary.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;

/** Comunidade fechada: não há diário aberto a visitantes deslogados. */
public enum Visibility {
    /**
     * Compartilhado com quem tem conta (feed, perfil e rankings). Aceita "PUBLIC" na entrada, valor antigo que um app
     * ainda em cache no aparelho pode mandar.
     */
    @JsonAlias("PUBLIC")
    COMMUNITY,
    /** Visível apenas para o autor. */
    PRIVATE
}
