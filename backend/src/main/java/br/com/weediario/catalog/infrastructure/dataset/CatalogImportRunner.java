package br.com.weediario.catalog.infrastructure.dataset;

import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase;
import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Importa o dataset de strains quando a aplicação sobe com {@code --weediario.catalog.import-file=<csv>}.
 * Com {@code --weediario.catalog.exit-after-import=true} encerra depois, útil como comando avulso:
 * <pre>
 * docker compose run --rm -v ./claude:/import:ro backend \
 *   --weediario.catalog.import-file=/import/cannabis-strains-final.csv --weediario.catalog.exit-after-import=true
 * </pre>
 */
@Component
public class CatalogImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportRunner.class);

    private final ImportDatasetUseCase importDataset;
    private final ConfigurableApplicationContext context;
    private final String importFile;
    private final boolean exitAfterImport;

    public CatalogImportRunner(ImportDatasetUseCase importDataset, ConfigurableApplicationContext context,
                               @Value("${weediario.catalog.import-file:}") String importFile,
                               @Value("${weediario.catalog.exit-after-import:false}") boolean exitAfterImport) {
        this.importDataset = importDataset;
        this.context = context;
        this.importFile = importFile;
        this.exitAfterImport = exitAfterImport;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (importFile.isBlank()) {
            return;
        }
        log.info("Importando dataset de strains de {}", importFile);
        try (Reader reader = Files.newBufferedReader(Path.of(importFile), StandardCharsets.UTF_8)) {
            Report report = importDataset.importStrains(DatasetCsvReader.read(reader));
            log.info("Dataset importado: {} linhas, {} strains novas, {} strains completadas, {} breeders novos, {} ignoradas",
                    report.rows(), report.strainsCreated(), report.strainsEnriched(), report.breedersCreated(),
                    report.skipped());
        }
        if (exitAfterImport) {
            System.exit(SpringApplication.exit(context, () -> 0));
        }
    }
}
