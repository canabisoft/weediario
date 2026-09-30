package br.com.weediario.catalog.infrastructure.config;

import br.com.weediario.catalog.application.query.BrowseCatalogUseCase;
import br.com.weediario.catalog.application.query.CatalogQueries;
import br.com.weediario.catalog.application.usecase.CurateCatalogUseCase;
import br.com.weediario.catalog.application.usecase.ImportDatasetUseCase;
import br.com.weediario.catalog.application.usecase.ResolveCatalogUseCase;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CatalogConfig {

    @Bean
    ResolveCatalogUseCase resolveCatalogUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        return new ResolveCatalogUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    CurateCatalogUseCase curateCatalogUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        return new CurateCatalogUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    ImportDatasetUseCase importDatasetUseCase(CatalogRepository catalog, UnitOfWork unitOfWork, Clock clock) {
        return new ImportDatasetUseCase(catalog, unitOfWork, clock);
    }

    @Bean
    BrowseCatalogUseCase browseCatalogUseCase(CatalogQueries queries) {
        return new BrowseCatalogUseCase(queries);
    }
}
