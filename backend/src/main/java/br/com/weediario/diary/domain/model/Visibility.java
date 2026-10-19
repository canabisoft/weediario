package br.com.weediario.diary.domain.model;

public enum Visibility {
    /** Aparece no feed público, para quem está logado ou não. */
    PUBLIC,
    /** Aparece só para quem está logado (feed, perfil e rankings); some para visitantes deslogados. */
    COMMUNITY,
    /** Visível apenas para o autor. */
    PRIVATE
}
