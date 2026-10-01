package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Conteúdo editável de uma receita: tudo que o autor escreve e uma replicação copia.
 *
 * @param batchLiters rendimento: litros de água (rega, chá, foliar) ou de substrato (mistura, cobertura)
 * @param targetEc    EC alvo da solução em mS/cm (só faz sentido em solução: rega ou foliar)
 * @param targetPh    pH alvo da solução
 */
public record RecipeDetails(String name, RecipeKind kind, RecipeApplication application, RecipeStage stage,
                            BigDecimal batchLiters, BigDecimal targetEc, BigDecimal targetPh, String notes,
                            List<Ingredient> ingredients) {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int NOTES_MAX_LENGTH = 2000;
    public static final int MAX_INGREDIENTS = 30;
    private static final BigDecimal MAX_BATCH_LITERS = new BigDecimal("10000");

    public RecipeDetails {
        name = Guard.lengthBetween(Guard.notBlank(name, "Nome da receita"), 1, NAME_MAX_LENGTH, "Nome da receita").strip();
        Guard.notNull(kind, "Tipo de receita");
        Guard.notNull(application, "Aplicação");
        Guard.notNull(batchLiters, "Rendimento");
        if (batchLiters.signum() <= 0 || batchLiters.compareTo(MAX_BATCH_LITERS) > 0) {
            throw new DomainException("O rendimento deve ser de mais de 0 até 10.000 litros");
        }
        boolean solution = application == RecipeApplication.FERTIGATION || application == RecipeApplication.FOLIAR;
        if (!solution && (targetEc != null || targetPh != null)) {
            throw new DomainException("EC e pH alvo só se aplicam a soluções de rega ou foliar");
        }
        if (targetEc != null && (targetEc.signum() < 0 || targetEc.compareTo(BigDecimal.TEN) > 0)) {
            throw new DomainException("EC alvo deve estar entre 0 e 10 mS/cm");
        }
        if (targetPh != null && (targetPh.signum() < 0 || targetPh.compareTo(new BigDecimal("14")) > 0)) {
            throw new DomainException("pH alvo deve estar entre 0 e 14");
        }
        notes = notes == null || notes.isBlank() ? null : Guard.lengthBetween(notes.strip(), 1, NOTES_MAX_LENGTH, "Observações");
        ingredients = List.copyOf(Guard.notNull(ingredients, "Ingredientes"));
        if (ingredients.isEmpty()) {
            throw new DomainException("A receita precisa de pelo menos um ingrediente");
        }
        if (ingredients.size() > MAX_INGREDIENTS) {
            throw new DomainException("Máximo de " + MAX_INGREDIENTS + " ingredientes por receita");
        }
        Set<Object> seen = new HashSet<>();
        for (Ingredient ingredient : ingredients) {
            Object key = ingredient.catalogProduct().<Object>map(id -> id)
                    .orElse(ingredient.name().toLowerCase(Locale.ROOT) + "|" + ingredient.brand());
            if (!seen.add(key)) {
                throw new DomainException("\"" + ingredient.name() + "\" aparece duas vezes na receita");
            }
        }
    }
}
