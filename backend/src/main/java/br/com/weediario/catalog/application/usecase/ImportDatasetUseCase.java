package br.com.weediario.catalog.application.usecase;

import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogKey;
import br.com.weediario.catalog.domain.model.CatalogNames;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Popula o catálogo a partir de um dataset público. Pode rodar várias vezes (idempotente):
 * <ul>
 *   <li>o nome é limpo antes (ver {@link CatalogNames}): "SALE - X - Banco" vira "X" do banco certo, "Banco - X"
 *   sem breeder vira "X" do banco, e brinde, pacote ou nome com preço é ignorado;</li>
 *   <li>strain nova → cadastrada com origem DATASET;</li>
 *   <li>strain já existente → só completa campos vazios, nunca sobrescreve o que a comunidade informou.</li>
 * </ul>
 */
public final class ImportDatasetUseCase {

    private static final int BATCH_SIZE = 500;

    private final CatalogRepository catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ImportDatasetUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Prefixo "X - " em pelo menos esta quantidade de linhas sem breeder é tratado como nome de banco. */
    static final int PREFIX_AS_BREEDER_MIN = 3;

    public Report importStrains(List<DatasetStrain> rows) {
        Set<String> frequentPrefixes = frequentPrefixes(rows);
        Map<String, Breeder> breeders = new HashMap<>();
        int[] counters = new int[4]; // strains criadas, strains completadas, breeders criados, ignoradas
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<DatasetStrain> batch = rows.subList(start, Math.min(rows.size(), start + BATCH_SIZE));
            unitOfWork.run(() -> batch.forEach(row -> importOne(row, frequentPrefixes, breeders, counters)));
        }
        return new Report(rows.size(), counters[0], counters[1], counters[2], counters[3]);
    }

    private void importOne(DatasetStrain raw, Set<String> frequentPrefixes, Map<String, Breeder> breeders,
                           int[] counters) {
        DatasetStrain row = cleaned(raw, frequentPrefixes);
        if (row == null || CatalogKey.of(row.name()).isEmpty() || CatalogNames.problemOf(row.name()).isPresent()
                || CatalogNames.problemOf(row.breederName()).isPresent()) {
            counters[3]++;
            return;
        }
        Breeder breeder = null;
        String breederKey = CatalogKey.breeder(row.breederName());
        if (!breederKey.isEmpty()) {
            breeder = breeders.computeIfAbsent(breederKey, key -> catalog.findBreederByKey(key).orElseGet(() -> {
                counters[2]++;
                return catalog.saveBreeder(Breeder.register(row.breederName(), Source.DATASET, null, clock));
            }));
        }
        var existing = catalog.findStrainByKey(CatalogKey.of(row.name()), breederKey);
        if (existing.isPresent()) {
            CatalogStrain strain = existing.get();
            if (strain.enrich(row.traits(), clock)) {
                catalog.saveStrain(strain);
                counters[1]++;
            }
            return;
        }
        catalog.saveStrain(CatalogStrain.register(row.name(), breeder, row.traits(), Source.DATASET, null, clock));
        counters[0]++;
    }

    /**
     * Tira o ruído de loja do nome. O breeder achado no nome só é usado se já existir no catálogo (o texto costuma vir
     * truncado, como "Seed City Bulk Canna").
     */
    private DatasetStrain cleaned(DatasetStrain row, Set<String> frequentPrefixes) {
        String breederName = row.breederName() == null || row.breederName().isBlank() ? null : row.breederName();
        CatalogNames.Cleaned noise = CatalogNames.stripStoreNoise(row.name(), breederName);
        String name = noise.name();
        if (breederName == null && noise.breeder() != null
                && catalog.findBreederByKey(CatalogKey.breeder(noise.breeder())).isPresent()) {
            breederName = noise.breeder();
        }
        if (breederName == null && name != null) {
            String prefix = name.split("\\s+-\\s+", 2)[0];
            boolean known = frequentPrefixes.contains(CatalogKey.of(prefix))
                    || catalog.findBreederByKey(CatalogKey.breeder(prefix)).isPresent();
            var split = CatalogNames.splitBreederPrefix(name, known);
            if (split.isPresent()) {
                name = split.get().name();
                breederName = split.get().breeder();
            }
        }
        if (breederName != null && name != null) {
            name = withoutBreederLine(name, breederName);
        }
        return name == null ? null : new DatasetStrain(name, breederName, row.traits());
    }

    /**
     * "Linha do Banco - X" quando o breeder já é conhecido ("Blimburn America - Gelato" do BlimBurn Seeds,
     * "Kera California - Gelato" do Kera Seeds): fica só "X".
     */
    static String withoutBreederLine(String name, String breederName) {
        String breederKey = CatalogKey.breeder(breederName);
        String[] parts = name.split("\\s+-\\s+", 2);
        if (breederKey.isEmpty() || parts.length < 2 || parts[1].isBlank()) {
            return name;
        }
        return CatalogKey.of(parts[0]).startsWith(breederKey) ? parts[1].strip() : name;
    }

    /** Prefixos "X - " repetidos em linhas sem breeder: "Professor Trichome - Gelato", "Professor Trichome - Runtz"... */
    private static Set<String> frequentPrefixes(List<DatasetStrain> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (DatasetStrain row : rows) {
            if ((row.breederName() == null || row.breederName().isBlank()) && row.name() != null
                    && row.name().contains(" - ") && !row.name().strip().toLowerCase(java.util.Locale.ROOT).startsWith("sale")) {
                counts.merge(CatalogKey.of(row.name().split("\\s+-\\s+", 2)[0]), 1, Integer::sum);
            }
        }
        Set<String> frequent = new java.util.HashSet<>();
        counts.forEach((prefix, count) -> {
            if (count >= PREFIX_AS_BREEDER_MIN && !prefix.isEmpty()) {
                frequent.add(prefix);
            }
        });
        return frequent;
    }

    /**
     * Linha do dataset já interpretada.
     *
     * @param breederName nulo quando o dataset não traz um breeder de verdade
     */
    public record DatasetStrain(String name, String breederName, StrainTraits traits) {
    }

    public record Report(int rows, int strainsCreated, int strainsEnriched, int breedersCreated, int skipped) {
    }
}
