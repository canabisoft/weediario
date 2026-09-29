package br.com.weediario.diary.domain.model;

/**
 * Cópia congelada do setup no momento em que o diário foi criado. Editar ou apagar o setup depois
 * não reescreve o histórico dos cultivos já feitos com ele.
 */
public record SetupSnapshot(String name, GrowEnvironment environment, SetupSpec spec) {
}
