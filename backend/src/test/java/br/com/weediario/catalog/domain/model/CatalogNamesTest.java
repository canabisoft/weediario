package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogNames.Cleaned;
import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Casos tirados da auditoria do dataset (loja de sementes raspada, com preços em libra). */
class CatalogNamesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {
            "1 Free Seed - £25 to £50", "14 Free Seeds - Over £1000", "Dank Mon£y", "Petro $ OG", "Kush R$ 30",
            "Gelato 25 GBP", "Auto Collection Pack #1", "CBD Mixed Pack", "Mix Pack Auto", "Grateful Casey Jones Mixpack",
            "5 Single Seed Storage Packs", "The Return of Chocolope - Collectors Pack", "Indica Champion Pack",
            "Diamond Indica Feminised Collection", "SALE - AK 48 - Nirvana Seeds", "Promoção: Gelato",
            "Feminised Mix", "Tutti Frutti Feminized Mix", "Sweet Mix Feminised", "T.H.Seeds - Anniversary Box - 6 Strains",
            "Seed City Hooded Zip Sweatshirt", "Seed City Unisex Tshirt", "Seed Tube Storage Rack",
            "Seed Bank Clothing"})
    void rejectsPricesFreebiesPacksAndAds(String name) {
        assertThat(CatalogNames.problemOf(name)).isPresent();
        assertThatThrownBy(() -> CatalogStrain.register(name, null, StrainTraits.EMPTY, Source.COMMUNITY, null, CLOCK))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Candy Packz", "Florida Gaspack", "Snack Pack", "Gorilla Glue #4", "22", "026", "TL;DR", "Blue Sky 99%",
            "CBD Harlequin 0.3/18%", "Exotic²", "Free Tibet", "Harlequeen THC Free", "Do-Si-Dos", "Gelato 41",
            "Biscotti x Off Road", "Kashmir 22 x 88G13/HP (Deep Line Alchemy 4)", "The Incredible Bulk", "OZ #6",
            "Knightsbridge OZ", "+ Speed", "Fresca Oz", "Blue Dream", "Bulk Smash Auto", "Pillow Talk", "Wedding Dress"})
    void keepsRealGeneticNames(String name) {
        assertThat(CatalogNames.problemOf(name)).isEmpty();
    }

    @Test
    void stripsStoreNoiseAndFindsTheBreederInsideTheName() {
        assertThat(CatalogNames.stripStoreNoise("SALE - Dank Mon£y - Dank Genetics", null))
                .isEqualTo(new Cleaned("Dank Mon£y", "Dank Genetics"));
        assertThat(CatalogNames.stripStoreNoise("SALE - ZkittleZ Glue - Expert Seeds - Expert Seeds - Expert Seed", null))
                .isEqualTo(new Cleaned("ZkittleZ Glue", "Expert Seeds"));
        assertThat(CatalogNames.stripStoreNoise("SALE - Afghani Milk -", null)).isEqualTo(new Cleaned("Afghani Milk", null));
        assertThat(CatalogNames.stripStoreNoise("SALE - Valencia Seeds - Amnesia Haze - Seed City Bulk Cannabis S", null))
                .isEqualTo(new Cleaned("Amnesia Haze", "Valencia Seeds"));
        assertThat(CatalogNames.stripStoreNoise("Northern Lights Regular Seeds", null))
                .isEqualTo(new Cleaned("Northern Lights", null));
        assertThat(CatalogNames.stripStoreNoise("Runtz - Valencia Seeds", "Valencia Seeds"))
                .isEqualTo(new Cleaned("Runtz", null));
        assertThat(CatalogNames.stripStoreNoise("Do-Si-Dos", null)).isEqualTo(new Cleaned("Do-Si-Dos", null));
    }

    @Test
    void splitsBreederPrefixOnlyWhenItLooksLikeABreeder() {
        assertThat(CatalogNames.splitBreederPrefix("Valencia Seeds - Runtz", false))
                .contains(new Cleaned("Runtz", "Valencia Seeds"));
        assertThat(CatalogNames.splitBreederPrefix("Good Buzz Genetics - Gelato", false))
                .contains(new Cleaned("Gelato", "Good Buzz Genetics"));
        assertThat(CatalogNames.splitBreederPrefix("Professor Trichome - Gelato", true))
                .contains(new Cleaned("Gelato", "Professor Trichome"));
        assertThat(CatalogNames.splitBreederPrefix("Professor Trichome - Gelato", false)).isEqualTo(Optional.empty());
        assertThat(CatalogNames.splitBreederPrefix("Gelato x Runtz", false)).isEmpty();
    }

    @Test
    void breedersFollowTheSameRule() {
        assertThatThrownBy(() -> Breeder.register("SALE - Seeds", Source.COMMUNITY, null, CLOCK))
                .isInstanceOf(DomainException.class);
        assertThat(Breeder.register("Valencia Seeds", Source.DATASET, null, CLOCK).name()).isEqualTo("Valencia Seeds");
    }
}
