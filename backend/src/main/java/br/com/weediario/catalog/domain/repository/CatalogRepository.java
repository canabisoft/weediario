package br.com.weediario.catalog.domain.repository;

import br.com.weediario.catalog.domain.model.Brand;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.Product;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do catálogo. As buscas por chave consideram também os apelidos (nomes juntados por moderadores).
 */
public interface CatalogRepository {

    Optional<CatalogStrain> findStrain(UUID id);

    Optional<CatalogStrain> findStrainByKey(String nameKey, String breederKey);

    CatalogStrain saveStrain(CatalogStrain strain);

    /** Candidatos parecidos (erro de digitação), do mais parecido para o menos. */
    List<CatalogStrain> similarStrains(String nameKey, String breederKey, double minSimilarity, int limit);

    Optional<Breeder> findBreeder(UUID id);

    Optional<Breeder> findBreederByKey(String nameKey);

    Breeder saveBreeder(Breeder breeder);

    List<Breeder> similarBreeders(String nameKey, double minSimilarity, int limit);

    Optional<Brand> findBrand(UUID id);

    Optional<Brand> findBrandByKey(String nameKey);

    Brand saveBrand(Brand brand);

    List<Brand> similarBrands(String nameKey, double minSimilarity, int limit);

    Optional<Product> findProduct(UUID id);

    List<Product> findProducts(Collection<UUID> ids);

    Optional<Product> findProductByKey(UUID brandId, String nameKey);

    Product saveProduct(Product product);

    List<Product> similarProducts(UUID brandId, String nameKey, double minSimilarity, int limit);

    /** Quantos itens a pessoa cadastrou desde o instante informado (limite anti-spam). */
    long countCreatedBy(UUID userId, Instant since);

    /**
     * Junta uma strain duplicada na principal: diários e reviews passam a apontar para a principal
     * e o nome da duplicada vira apelido dela.
     */
    void mergeStrain(UUID duplicateId, UUID targetId);
}
