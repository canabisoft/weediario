package br.com.weediario.moderation.domain.model;

import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.shared.domain.Guard;

import java.util.UUID;

/** Referência a um conteúdo de qualquer contexto (comentário, diário, review, receita, perfil...). */
public record ContentRef(TargetType type, UUID id) {

    public ContentRef {
        Guard.notNull(type, "Tipo de conteúdo");
        Guard.notNull(id, "Conteúdo");
    }
}
