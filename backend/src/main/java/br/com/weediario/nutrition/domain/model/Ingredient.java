package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Um item da receita.
 * <p>
 * Com {@code productId}: produto de marca do catálogo (nome e marca congelados, conta no ranking de "Produtos").
 * Sem: sal ou insumo genérico pelo nome, como "Nitrato de cálcio" ou "Húmus de minhoca" (a marca é opcional).
 */
public record Ingredient(UUID productId, String name, String brand, BigDecimal quantity, IngredientUnit unit) {

    public static final int NAME_MAX_LENGTH = 80;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("100000");

    public Ingredient {
        name = Guard.lengthBetween(Guard.notBlank(name, "Ingrediente"), 1, NAME_MAX_LENGTH, "Ingrediente").strip();
        brand = brand == null || brand.isBlank() ? null : Guard.lengthBetween(brand.strip(), 1, NAME_MAX_LENGTH, "Marca");
        Guard.notNull(quantity, "Quantidade");
        if (quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new DomainException("Quantidade de \"" + name + "\" deve ser maior que zero e realista");
        }
        if (quantity.scale() > 3) {
            throw new DomainException("Use no máximo 3 casas decimais na quantidade");
        }
        Guard.notNull(unit, "Unidade");
    }

    public static Ingredient generic(String name, String brand, BigDecimal quantity, IngredientUnit unit) {
        return new Ingredient(null, name, brand, quantity, unit);
    }

    public Optional<UUID> catalogProduct() {
        return Optional.ofNullable(productId);
    }
}
