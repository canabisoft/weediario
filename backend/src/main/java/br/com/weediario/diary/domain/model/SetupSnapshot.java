package br.com.weediario.diary.domain.model;

import java.util.List;

/**
 * Cópia congelada do setup no momento em que o diário foi criado. Editar ou apagar o setup depois
 * não reescreve o histórico dos cultivos já feitos com ele. Só a lista de produtos pode ser corrigida pelo
 * grower no próprio cultivo; {@code name} é nulo quando o cultivo não partiu de um setup.
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

    /** Cultivo aberto sem setup, mas com produtos informados depois: só a lista de produtos, sem nome. */
    public static SetupSnapshot productsOnly(List<ProductSnapshot> products) {
        return new SetupSnapshot(null, null, null, products);
    }

    public SetupSnapshot withProducts(List<ProductSnapshot> newProducts) {
        return new SetupSnapshot(name, environment, spec, newProducts);
    }
}
