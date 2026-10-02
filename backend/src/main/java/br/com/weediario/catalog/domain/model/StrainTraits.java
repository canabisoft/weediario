package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringSpeed;
import br.com.weediario.catalog.domain.model.CatalogEnums.SeedKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.StrainGeneration;
import br.com.weediario.shared.domain.DomainException;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Características de uma genética. Tudo opcional: o dataset e a comunidade vão completando aos poucos.
 *
 * @param generation geração (F1, F2, S1, IBL...), informada sobretudo por breeders no programa
 */
public record StrainTraits(
        FloweringKind floweringType,
        Dominance dominance,
        FloweringSpeed floweringSpeed,
        Set<SeedKind> seedTypes,
        List<String> flavors,
        List<String> effects,
        String genetics,
        StrainGeneration generation) {

    public static final int MAX_TAGS = 12;
    public static final int TAG_MAX_LENGTH = 30;
    public static final int GENETICS_MAX_LENGTH = 120;

    public static final StrainTraits EMPTY = new StrainTraits(null, null, null, Set.of(), List.of(), List.of(), null, null);

    /** Sem geração informada. */
    public StrainTraits(FloweringKind floweringType, Dominance dominance, FloweringSpeed floweringSpeed,
                        Set<SeedKind> seedTypes, List<String> flavors, List<String> effects, String genetics) {
        this(floweringType, dominance, floweringSpeed, seedTypes, flavors, effects, genetics, null);
    }

    public StrainTraits {
        seedTypes = seedTypes == null || seedTypes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(seedTypes));
        flavors = tags(flavors, "Sabores");
        effects = tags(effects, "Efeitos");
        genetics = genetics == null || genetics.isBlank() ? null : genetics.replaceAll("\\p{Cntrl}", "").strip();
        if (genetics != null && genetics.length() > GENETICS_MAX_LENGTH) {
            throw new DomainException("Genética deve ter no máximo " + GENETICS_MAX_LENGTH + " caracteres");
        }
    }

    /**
     * Enriquecimento: completa só o que está vazio aqui, sem sobrescrever o que já foi informado.
     */
    public StrainTraits fillMissingFrom(StrainTraits other) {
        return new StrainTraits(
                floweringType != null ? floweringType : other.floweringType,
                dominance != null ? dominance : other.dominance,
                floweringSpeed != null ? floweringSpeed : other.floweringSpeed,
                seedTypes.isEmpty() ? other.seedTypes : seedTypes,
                flavors.isEmpty() ? other.flavors : flavors,
                effects.isEmpty() ? other.effects : effects,
                genetics != null ? genetics : other.genetics,
                generation != null ? generation : other.generation);
    }

    public boolean isComplete() {
        return floweringType != null && dominance != null && floweringSpeed != null && !seedTypes.isEmpty()
                && !flavors.isEmpty() && !effects.isEmpty() && genetics != null;
    }

    /** Tags em minúsculas, sem repetição e com limite de quantidade e tamanho. */
    private static List<String> tags(List<String> raw, String field) {
        if (raw == null) {
            return List.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String value = tag.replaceAll("\\p{Cntrl}", "").strip().toLowerCase(Locale.ROOT);
            if (value.length() > TAG_MAX_LENGTH) {
                throw new DomainException(field + ": cada item deve ter no máximo " + TAG_MAX_LENGTH + " caracteres");
            }
            cleaned.add(value);
        }
        if (cleaned.size() > MAX_TAGS) {
            throw new DomainException(field + ": no máximo " + MAX_TAGS + " itens");
        }
        return List.copyOf(cleaned);
    }
}
