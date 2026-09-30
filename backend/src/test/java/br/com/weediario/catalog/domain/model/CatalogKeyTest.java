package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogKeyTest {

    @Test
    void typingVariationsCollapseToTheSameKey() {
        assertThat(CatalogKey.of("Gorilla Glue #4")).isEqualTo(CatalogKey.of("gorilla-glue  4")).isEqualTo("gorilla glue 4");
        assertThat(CatalogKey.of("Açaí Kush")).isEqualTo("acai kush");
        assertThat(CatalogKey.of("Mother's Milk")).isEqualTo(CatalogKey.of("Mothers Milk"));
    }

    @Test
    void breederKeyDropsGenericShopSuffixes() {
        assertThat(CatalogKey.breeder("Barneys Farm Seeds")).isEqualTo(CatalogKey.breeder("Barney's Farm")).isEqualTo("barneys farm");
        assertThat(CatalogKey.breeder("Sensi Seed Bank")).isEqualTo("sensi");
        assertThat(CatalogKey.breeder("Seed Stockers")).isEqualTo("seed stockers");
        assertThat(CatalogKey.breeder(null)).isEmpty();
    }

    @Test
    void enrichmentOnlyFillsWhatIsMissing() {
        StrainTraits known = new StrainTraits(null, Dominance.INDICA, null, Set.of(), List.of("doce"), List.of(), null);
        StrainTraits contribution = new StrainTraits(null, Dominance.SATIVA, null, Set.of(), List.of("cítrico"),
                List.of("relaxante"), "Afghani x Thai");

        StrainTraits merged = known.fillMissingFrom(contribution);

        assertThat(merged.dominance()).isEqualTo(Dominance.INDICA);
        assertThat(merged.flavors()).containsExactly("doce");
        assertThat(merged.effects()).containsExactly("relaxante");
        assertThat(merged.genetics()).isEqualTo("Afghani x Thai");
    }

    @Test
    void tagsAreNormalizedAndLimited() {
        StrainTraits traits = new StrainTraits(null, null, null, null, List.of(" Doce ", "doce", "Cítrico"), null, null);
        assertThat(traits.flavors()).containsExactly("doce", "cítrico");

        List<String> tooMany = java.util.stream.IntStream.range(0, 13).mapToObj(i -> "tag" + i).toList();
        assertThatThrownBy(() -> new StrainTraits(null, null, null, null, tooMany, null, null))
                .isInstanceOf(DomainException.class);
    }
}
