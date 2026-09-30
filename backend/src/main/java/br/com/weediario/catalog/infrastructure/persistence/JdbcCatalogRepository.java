package br.com.weediario.catalog.infrastructure.persistence;

import br.com.weediario.catalog.domain.model.Brand;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.CatalogEnums.Dominance;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.FloweringSpeed;
import br.com.weediario.catalog.domain.model.CatalogEnums.ProductCategory;
import br.com.weediario.catalog.domain.model.CatalogEnums.SeedKind;
import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.Product;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcCatalogRepository implements CatalogRepository {

    private static final String STRAIN_SELECT = """
            select s.*, b.id as b_id, b.name as b_name, b.name_key as b_name_key, b.status as b_status,
                   b.source as b_source, b.created_by as b_created_by, b.created_at as b_created_at
              from strains s
              left join breeders b on b.id = s.breeder_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------------------------------- strains

    @Override
    public Optional<CatalogStrain> findStrain(UUID id) {
        return first(jdbc.query(STRAIN_SELECT + " where s.id = :id", new MapSqlParameterSource("id", id), this::strain));
    }

    @Override
    public Optional<CatalogStrain> findStrainByKey(String nameKey, String breederKey) {
        // A chave pode estar direto na strain ou num apelido (nome de uma duplicada que foi juntada nela).
        return first(jdbc.query(STRAIN_SELECT + """
                 where (s.name_key = :key and s.breeder_key = :breederKey)
                    or s.id = (select a.strain_id from strain_aliases a
                                where a.alias_key = :key and a.breeder_key = :breederKey)
                 limit 1""",
                new MapSqlParameterSource("key", nameKey).addValue("breederKey", breederKey), this::strain));
    }

    @Override
    public CatalogStrain saveStrain(CatalogStrain strain) {
        StrainTraits traits = strain.traits();
        jdbc.update("""
                insert into strains (id, name, breeder, name_key, breeder_key, breeder_id, flowering_type, dominance,
                                     flowering_speed, seed_types, flavors, effects, genetics, status, source,
                                     created_by, created_at, updated_at)
                values (:id, :name, :breeder, :nameKey, :breederKey, :breederId, :floweringType, :dominance,
                        :floweringSpeed, cast(:seedTypes as varchar(20)[]), cast(:flavors as varchar(30)[]),
                        cast(:effects as varchar(30)[]), :genetics, :status, :source, :createdBy, :createdAt, :updatedAt)
                on conflict (id) do update set
                    flowering_type = excluded.flowering_type,
                    dominance = excluded.dominance,
                    flowering_speed = excluded.flowering_speed,
                    seed_types = excluded.seed_types,
                    flavors = excluded.flavors,
                    effects = excluded.effects,
                    genetics = excluded.genetics,
                    status = excluded.status,
                    updated_at = excluded.updated_at""",
                new MapSqlParameterSource("id", strain.id())
                        .addValue("name", strain.name())
                        .addValue("breeder", strain.breeder() == null ? null : strain.breeder().name())
                        .addValue("nameKey", strain.nameKey())
                        .addValue("breederKey", strain.breederKey())
                        .addValue("breederId", strain.breeder() == null ? null : strain.breeder().id())
                        .addValue("floweringType", name(traits.floweringType()))
                        .addValue("dominance", name(traits.dominance()))
                        .addValue("floweringSpeed", name(traits.floweringSpeed()))
                        .addValue("seedTypes", traits.seedTypes().stream().map(Enum::name).sorted().toArray(String[]::new))
                        .addValue("flavors", traits.flavors().toArray(String[]::new))
                        .addValue("effects", traits.effects().toArray(String[]::new))
                        .addValue("genetics", traits.genetics())
                        .addValue("status", strain.status().name())
                        .addValue("source", strain.source().name())
                        .addValue("createdBy", strain.createdBy(), Types.OTHER)
                        .addValue("createdAt", Timestamp.from(strain.createdAt()))
                        .addValue("updatedAt", Timestamp.from(strain.updatedAt())));
        return strain;
    }

    @Override
    public List<CatalogStrain> similarStrains(String nameKey, String breederKey, double minSimilarity, int limit) {
        return jdbc.query(STRAIN_SELECT + """
                 where similarity(s.name_key, :key) >= :min
                 order by (s.breeder_key = :breederKey) desc, similarity(s.name_key, :key) desc, s.name
                 limit :limit""",
                new MapSqlParameterSource("key", nameKey).addValue("breederKey", breederKey)
                        .addValue("min", minSimilarity).addValue("limit", limit),
                this::strain);
    }

    @Override
    public void mergeStrain(UUID duplicateId, UUID targetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("dup", duplicateId).addValue("target", targetId);
        jdbc.update("""
                insert into strain_aliases (alias_key, breeder_key, strain_id)
                select name_key, breeder_key, :target from strains where id = :dup
                on conflict (alias_key, breeder_key) do update set strain_id = excluded.strain_id""", params);
        jdbc.update("update strain_aliases set strain_id = :target where strain_id = :dup", params);
        jdbc.update("""
                update grow_diaries
                   set strain_id = :target, breeder_id = (select breeder_id from strains where id = :target)
                 where strain_id = :dup""", params);
        jdbc.update("update reviews set strain_id = :target where strain_id = :dup", params);
        jdbc.update("delete from strains where id = :dup", params);
    }

    @Override
    public List<Collision> strainCollisions(UUID duplicateBreederId, UUID targetBreederId) {
        return jdbc.query("""
                select d.id as dup, t.id as target
                  from strains d
                  join strains t on t.breeder_id = :target and t.name_key = d.name_key
                 where d.breeder_id = :dup""",
                new MapSqlParameterSource("dup", duplicateBreederId).addValue("target", targetBreederId),
                (rs, n) -> new Collision(rs.getObject("dup", UUID.class), rs.getObject("target", UUID.class)));
    }

    @Override
    public void mergeBreeder(UUID duplicateId, UUID targetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("dup", duplicateId).addValue("target", targetId);
        // Apelidos de strains ficam presos à chave do breeder: passam para a chave do principal (sem duplicar).
        jdbc.update("""
                delete from strain_aliases a
                 using breeders d, breeders t
                 where d.id = :dup and t.id = :target and a.breeder_key = d.name_key
                   and exists (select 1 from strain_aliases o where o.breeder_key = t.name_key and o.alias_key = a.alias_key)""",
                params);
        jdbc.update("""
                update strain_aliases a set breeder_key = t.name_key
                  from breeders d, breeders t
                 where d.id = :dup and t.id = :target and a.breeder_key = d.name_key""", params);
        jdbc.update("""
                update strains s set breeder_id = t.id, breeder = t.name, breeder_key = t.name_key
                  from breeders t
                 where t.id = :target and s.breeder_id = :dup""", params);
        jdbc.update("update grow_diaries set breeder_id = :target where breeder_id = :dup", params);
        jdbc.update("update reviews set seedbank_id = :target where seedbank_id = :dup", params);
        jdbc.update("""
                insert into breeder_aliases (alias_key, breeder_id)
                select name_key, :target from breeders where id = :dup
                on conflict (alias_key) do update set breeder_id = excluded.breeder_id""", params);
        jdbc.update("update breeder_aliases set breeder_id = :target where breeder_id = :dup", params);
        jdbc.update("delete from breeders where id = :dup", params);
    }

    @Override
    public List<Collision> productCollisions(UUID duplicateBrandId, UUID targetBrandId) {
        return jdbc.query("""
                select d.id as dup, t.id as target
                  from products d
                  join products t on t.brand_id = :target and t.name_key = d.name_key
                 where d.brand_id = :dup""",
                new MapSqlParameterSource("dup", duplicateBrandId).addValue("target", targetBrandId),
                (rs, n) -> new Collision(rs.getObject("dup", UUID.class), rs.getObject("target", UUID.class)));
    }

    @Override
    public void mergeBrand(UUID duplicateId, UUID targetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("dup", duplicateId).addValue("target", targetId);
        jdbc.update("""
                delete from product_aliases a
                 where a.brand_id = :dup
                   and exists (select 1 from product_aliases o where o.brand_id = :target and o.alias_key = a.alias_key)""",
                params);
        jdbc.update("update product_aliases set brand_id = :target where brand_id = :dup", params);
        jdbc.update("update products set brand_id = :target where brand_id = :dup", params);
        jdbc.update("""
                insert into brand_aliases (alias_key, brand_id)
                select name_key, :target from brands where id = :dup
                on conflict (alias_key) do update set brand_id = excluded.brand_id""", params);
        jdbc.update("update brand_aliases set brand_id = :target where brand_id = :dup", params);
        jdbc.update("delete from brands where id = :dup", params);
    }

    @Override
    public void mergeProduct(UUID duplicateId, UUID targetId) {
        MapSqlParameterSource params = new MapSqlParameterSource("dup", duplicateId).addValue("target", targetId);
        jdbc.update("""
                insert into diary_products (diary_id, product_id)
                select diary_id, :target from diary_products where product_id = :dup
                on conflict do nothing""", params);
        jdbc.update("delete from diary_products where product_id = :dup", params);
        jdbc.update("update grow_setup_products set product_id = :target where product_id = :dup", params);
        jdbc.update("""
                insert into product_aliases (brand_id, alias_key, product_id)
                select brand_id, name_key, :target from products where id = :dup
                on conflict (brand_id, alias_key) do update set product_id = excluded.product_id""", params);
        jdbc.update("update product_aliases set product_id = :target where product_id = :dup", params);
        jdbc.update("delete from products where id = :dup", params);
    }

    /** Referências que impedem apagar um item (tabela e coluna, nunca texto do usuário). */
    private static final Map<ItemKind, String> IN_USE = Map.of(
            ItemKind.STRAIN, """
                    exists (select 1 from grow_diaries where strain_id = :id)
                    or exists (select 1 from reviews where strain_id = :id)""",
            ItemKind.BREEDER, """
                    exists (select 1 from grow_diaries where breeder_id = :id)
                    or exists (select 1 from reviews where seedbank_id = :id)
                    or exists (select 1 from strains where breeder_id = :id)""",
            ItemKind.BRAND, "exists (select 1 from products where brand_id = :id)",
            ItemKind.PRODUCT, """
                    exists (select 1 from diary_products where product_id = :id)
                    or exists (select 1 from grow_setup_products where product_id = :id)""");

    private static final Map<ItemKind, String> TABLE = Map.of(
            ItemKind.STRAIN, "strains", ItemKind.BREEDER, "breeders", ItemKind.BRAND, "brands",
            ItemKind.PRODUCT, "products");

    @Override
    public boolean deleteIfUnused(ItemKind kind, UUID id) {
        return jdbc.update("delete from " + TABLE.get(kind) + " where id = :id and not (" + IN_USE.get(kind) + ")",
                new MapSqlParameterSource("id", id)) > 0;
    }

    // --------------------------------------------------------------------------------------- breeders

    @Override
    public Optional<Breeder> findBreeder(UUID id) {
        return first(jdbc.query("select * from breeders where id = :id", new MapSqlParameterSource("id", id),
                (rs, n) -> breeder(rs, "")));
    }

    @Override
    public Optional<Breeder> findBreederByKey(String nameKey) {
        return first(jdbc.query("""
                select * from breeders
                 where name_key = :key
                    or id = (select a.breeder_id from breeder_aliases a where a.alias_key = :key)
                 order by (name_key = :key) desc
                 limit 1""",
                new MapSqlParameterSource("key", nameKey), (rs, n) -> breeder(rs, "")));
    }

    @Override
    public Breeder saveBreeder(Breeder breeder) {
        jdbc.update("""
                insert into breeders (id, name, name_key, status, source, created_by, created_at)
                values (:id, :name, :key, :status, :source, :createdBy, :createdAt)
                on conflict (id) do update set status = excluded.status""",
                namedParams(breeder.id(), breeder.name(), breeder.nameKey(), breeder.status(), breeder.source(),
                        breeder.createdBy(), breeder.createdAt()));
        return breeder;
    }

    @Override
    public List<Breeder> similarBreeders(String nameKey, double minSimilarity, int limit) {
        return jdbc.query("""
                select * from breeders where similarity(name_key, :key) >= :min
                 order by similarity(name_key, :key) desc, name limit :limit""",
                new MapSqlParameterSource("key", nameKey).addValue("min", minSimilarity).addValue("limit", limit),
                (rs, n) -> breeder(rs, ""));
    }

    // ----------------------------------------------------------------------------------------- brands

    @Override
    public Optional<Brand> findBrand(UUID id) {
        return first(jdbc.query("select * from brands where id = :id", new MapSqlParameterSource("id", id), this::brand));
    }

    @Override
    public Optional<Brand> findBrandByKey(String nameKey) {
        return first(jdbc.query("""
                select * from brands
                 where name_key = :key
                    or id = (select a.brand_id from brand_aliases a where a.alias_key = :key)
                 order by (name_key = :key) desc
                 limit 1""",
                new MapSqlParameterSource("key", nameKey), this::brand));
    }

    @Override
    public Brand saveBrand(Brand brand) {
        jdbc.update("""
                insert into brands (id, name, name_key, status, source, created_by, created_at)
                values (:id, :name, :key, :status, :source, :createdBy, :createdAt)
                on conflict (id) do update set status = excluded.status""",
                namedParams(brand.id(), brand.name(), brand.nameKey(), brand.status(), brand.source(), brand.createdBy(),
                        brand.createdAt()));
        return brand;
    }

    @Override
    public List<Brand> similarBrands(String nameKey, double minSimilarity, int limit) {
        return jdbc.query("""
                select * from brands where similarity(name_key, :key) >= :min
                 order by similarity(name_key, :key) desc, name limit :limit""",
                new MapSqlParameterSource("key", nameKey).addValue("min", minSimilarity).addValue("limit", limit),
                this::brand);
    }

    // --------------------------------------------------------------------------------------- products

    @Override
    public Optional<Product> findProduct(UUID id) {
        return first(jdbc.query("select * from products where id = :id", new MapSqlParameterSource("id", id), this::product));
    }

    @Override
    public List<Product> findProducts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("select * from products where id in (:ids)", new MapSqlParameterSource("ids", ids),
                this::product);
    }

    @Override
    public Optional<Product> findProductByKey(UUID brandId, String nameKey) {
        return first(jdbc.query("""
                select * from products
                 where (brand_id = :brandId and name_key = :key)
                    or id = (select a.product_id from product_aliases a where a.brand_id = :brandId and a.alias_key = :key)
                 order by (brand_id = :brandId and name_key = :key) desc
                 limit 1""",
                new MapSqlParameterSource("brandId", brandId).addValue("key", nameKey), this::product));
    }

    @Override
    public Product saveProduct(Product product) {
        jdbc.update("""
                insert into products (id, brand_id, name, name_key, category, status, source, created_by, created_at)
                values (:id, :brandId, :name, :key, :category, :status, :source, :createdBy, :createdAt)
                on conflict (id) do update set status = excluded.status""",
                namedParams(product.id(), product.name(), product.nameKey(), product.status(), product.source(),
                        product.createdBy(), product.createdAt())
                        .addValue("brandId", product.brandId())
                        .addValue("category", product.category().name()));
        return product;
    }

    @Override
    public List<Product> similarProducts(UUID brandId, String nameKey, double minSimilarity, int limit) {
        return jdbc.query("""
                select * from products where brand_id = :brandId and similarity(name_key, :key) >= :min
                 order by similarity(name_key, :key) desc, name limit :limit""",
                new MapSqlParameterSource("brandId", brandId).addValue("key", nameKey)
                        .addValue("min", minSimilarity).addValue("limit", limit),
                this::product);
    }

    @Override
    public long countCreatedBy(UUID userId, Instant since) {
        Long count = jdbc.queryForObject("""
                select (select count(*) from strains where created_by = :user and created_at >= :since)
                     + (select count(*) from breeders where created_by = :user and created_at >= :since)
                     + (select count(*) from brands where created_by = :user and created_at >= :since)
                     + (select count(*) from products where created_by = :user and created_at >= :since)""",
                new MapSqlParameterSource("user", userId).addValue("since", Timestamp.from(since)), Long.class);
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------------------------------- mappers

    private CatalogStrain strain(ResultSet rs, int rowNum) throws SQLException {
        Breeder breeder = rs.getObject("b_id", UUID.class) == null ? null : breeder(rs, "b_");
        StrainTraits traits = new StrainTraits(
                enumOf(FloweringKind.class, rs.getString("flowering_type")),
                enumOf(Dominance.class, rs.getString("dominance")),
                enumOf(FloweringSpeed.class, rs.getString("flowering_speed")),
                seedKinds(rs.getArray("seed_types")),
                strings(rs.getArray("flavors")),
                strings(rs.getArray("effects")),
                rs.getString("genetics"));
        return CatalogStrain.restore(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                breeder,
                traits,
                Status.valueOf(rs.getString("status")),
                Source.valueOf(rs.getString("source")),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Breeder breeder(ResultSet rs, String prefix) throws SQLException {
        return new Breeder(
                rs.getObject(prefix + "id", UUID.class),
                rs.getString(prefix + "name"),
                rs.getString(prefix + "name_key"),
                Status.valueOf(rs.getString(prefix + "status")),
                Source.valueOf(rs.getString(prefix + "source")),
                rs.getObject(prefix + "created_by", UUID.class),
                rs.getTimestamp(prefix + "created_at").toInstant());
    }

    private Brand brand(ResultSet rs, int rowNum) throws SQLException {
        return new Brand(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("name_key"),
                Status.valueOf(rs.getString("status")),
                Source.valueOf(rs.getString("source")),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private Product product(ResultSet rs, int rowNum) throws SQLException {
        return new Product(
                rs.getObject("id", UUID.class),
                rs.getObject("brand_id", UUID.class),
                rs.getString("name"),
                rs.getString("name_key"),
                ProductCategory.valueOf(rs.getString("category")),
                Status.valueOf(rs.getString("status")),
                Source.valueOf(rs.getString("source")),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private static MapSqlParameterSource namedParams(UUID id, String name, String key, Status status, Source source,
                                                     UUID createdBy, Instant createdAt) {
        return new MapSqlParameterSource("id", id)
                .addValue("name", name)
                .addValue("key", key)
                .addValue("status", status.name())
                .addValue("source", source.name())
                .addValue("createdBy", createdBy, Types.OTHER)
                .addValue("createdAt", Timestamp.from(createdAt));
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static Set<SeedKind> seedKinds(Array array) throws SQLException {
        List<String> values = strings(array);
        return values.isEmpty() ? Set.of()
                : values.stream().map(SeedKind::valueOf).collect(Collectors.toCollection(() -> EnumSet.noneOf(SeedKind.class)));
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <T> Optional<T> first(List<T> list) {
        return list.stream().findFirst();
    }
}
