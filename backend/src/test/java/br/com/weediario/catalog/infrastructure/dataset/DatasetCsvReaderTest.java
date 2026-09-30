package br.com.weediario.catalog.infrastructure.dataset;

import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase.DatasetStrain;
import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringSpeed;
import br.com.weediario.catalog.domain.model.CatalogEnums.SeedKind;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class DatasetCsvReaderTest {

    public static List<DatasetStrain> sample() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                DatasetCsvReaderTest.class.getResourceAsStream("/catalog/strains-sample.csv")), StandardCharsets.UTF_8)) {
            return DatasetCsvReader.read(reader);
        }
    }

    @Test
    void mapsReliableFieldsAndTranslatesTags() throws Exception {
        DatasetStrain bubba = sample().get(0);

        assertThat(bubba.name()).isEqualTo("Bubba Kush");
        assertThat(bubba.breederName()).isEqualTo("Teste Genetics Seeds");
        assertThat(bubba.traits().floweringType()).isEqualTo(FloweringKind.PHOTOPERIOD);
        assertThat(bubba.traits().dominance()).isEqualTo(Dominance.INDICA_DOMINANT);
        assertThat(bubba.traits().floweringSpeed()).isEqualTo(FloweringSpeed.MEDIUM);
        assertThat(bubba.traits().seedTypes()).containsExactly(SeedKind.FEMINIZED);
        assertThat(bubba.traits().flavors()).containsExactly("terroso", "doce", "café");
        assertThat(bubba.traits().effects()).containsExactly("relaxante", "sonolento");
    }

    @Test
    void dropsFillerGeneticsButKeepsRealOnes() throws Exception {
        List<DatasetStrain> rows = sample();

        assertThat(rows.get(0).traits().genetics()).isNull();
        assertThat(rows.get(1).traits().genetics()).isEqualTo("Afghani x Thai");
    }

    @Test
    void translatesCompoundTermsWordByWordAndIgnoresUnknownOnes() throws Exception {
        DatasetStrain lemon = sample().get(1);

        assertThat(lemon.traits().flavors()).containsExactly("limão", "doce", "picante", "cítrico");
        assertThat(lemon.traits().effects()).containsExactly("animado", "feliz", "calmante");
        assertThat(lemon.traits().seedTypes()).containsExactlyInAnyOrder(SeedKind.FEMINIZED, SeedKind.REGULAR);
        assertThat(lemon.traits().floweringType()).isEqualTo(FloweringKind.AUTOFLOWER);
        assertThat(lemon.traits().dominance()).isEqualTo(Dominance.HYBRID);
    }

    @Test
    void fillerFlavorAndEffectAloneAreIgnored() {
        assertThat(DatasetCsvReader.unlessFiller("Citrus", "citrus")).isNull();
        assertThat(DatasetCsvReader.unlessFiller("Citrus, Lemon", "citrus")).isEqualTo("Citrus, Lemon");
        assertThat(DatasetCsvReader.unlessFiller(" Relaxing ", "relaxing")).isNull();
    }

    @Test
    void ambiguousValuesAndShopCategoriesBecomeEmpty() throws Exception {
        DatasetStrain mystery = sample().get(2);

        assertThat(mystery.breederName()).isNull();
        assertThat(mystery.traits().floweringType()).isNull();
        assertThat(mystery.traits().dominance()).isNull();
        assertThat(mystery.traits().floweringSpeed()).isNull();
    }
}
