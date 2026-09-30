package br.com.weediario.diary.domain.model;

import java.util.List;

/**
 * Cópia congelada do setup no momento em que o diário foi criado. Editar ou apagar o setup depois
 * não reescreve o histórico dos cultivos já feitos com ele.
 *
 * @param products produtos do catálogo usados no setup (fertilizantes, substratos...)
 */
public record SetupSnapshot(String name, GrowEnvironment environment, SetupSpec spec, List<ProductSnapshot> products) {

    public SetupSnapshot {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public SetupSnapshot(String name, GrowEnvironment environment, SetupSpec spec) {
        this(name, environment, spec, List.of());
    }
}
