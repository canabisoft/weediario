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

    /**
     * Strains do breeder duplicado que já existem com o mesmo nome no breeder principal
     * (precisam ser juntadas antes de mover o resto).
     */
    List<Collision> strainCollisions(UUID duplicateBreederId, UUID targetBreederId);

    /**
     * Junta um breeder duplicado no principal: strains, diários e reviews passam para o principal,
     * e o nome do duplicado vira apelido. Colisões de strains precisam ter sido resolvidas antes.
     */
    void mergeBreeder(UUID duplicateId, UUID targetId);

    /** Produtos da marca duplicada que já existem com o mesmo nome na marca principal. */
    List<Collision> productCollisions(UUID duplicateBrandId, UUID targetBrandId);

    /** Junta uma marca duplicada na principal. Colisões de produtos precisam ter sido resolvidas antes. */
    void mergeBrand(UUID duplicateId, UUID targetId);

    /** Junta um produto duplicado no principal: setups e diários passam a usar o principal. */
    void mergeProduct(UUID duplicateId, UUID targetId);

    /**
     * Apaga um item que ninguém usa (cadastro errado ou spam).
     *
     * @return {@code false} se o item está em uso (diário, review, setup, strain ou produto ligado a ele)
     */
    boolean deleteIfUnused(ItemKind kind, UUID id);

    enum ItemKind {
        STRAIN,
        BREEDER,
        BRAND,
        PRODUCT
    }

    /** Par duplicado/principal com o mesmo nome. */
    record Collision(UUID duplicateId, UUID targetId) {
    }
}
