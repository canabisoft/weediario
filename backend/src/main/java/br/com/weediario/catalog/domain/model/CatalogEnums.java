package br.com.weediario.catalog.domain.model;

/**
 * Enumerações do catálogo, agrupadas por serem pequenas e usadas juntas.
 */
public final class CatalogEnums {

    private CatalogEnums() {
    }

    /**
     * Verificado (por um moderador ou, para sementes de breeder, pelo teste da comunidade), ainda não revisado,
     * ou em teste: semente cadastrada pelo breeder que ainda não entrou no banco oficial (só strains).
     */
    public enum Status {
        UNVERIFIED,
        VERIFIED,
        TESTING
    }

    /** De onde veio o item: dataset público importado, cadastrado pela comunidade ou pelo próprio breeder. */
    public enum Source {
        DATASET,
        COMMUNITY,
        BREEDER
    }

    public enum FloweringKind {
        PHOTOPERIOD,
        AUTOFLOWER
    }

    public enum Dominance {
        INDICA,
        INDICA_DOMINANT,
        HYBRID,
        SATIVA_DOMINANT,
        SATIVA
    }

    /** Faixas de tempo de flora indoor: curta (até 55 dias), média (56 a 90), longa (91+). */
    public enum FloweringSpeed {
        SHORT,
        MEDIUM,
        LONG
    }

    public enum SeedKind {
        FEMINIZED,
        REGULAR
    }

    /** Banco de sementes (empresa ou marca) ou breeder independente (quem cria genéticas por conta própria). */
    public enum BreederKind {
        SEEDBANK,
        INDEPENDENT
    }

    /**
     * Geração da genética, que indica o quanto ela é estável: F1 (primeiro cruzamento), F2, F3 ou mais, S1
     * (autocruzamento), BX (retrocruzamento), IBL (linhagem estável) ou poli-híbrido.
     */
    public enum StrainGeneration {
        F1,
        F2,
        F3_PLUS,
        S1,
        BX,
        IBL,
        POLYHYBRID
    }

    public enum ProductCategory {
        NUTRIENT,
        ADDITIVE,
        SUBSTRATE,
        PEST_CONTROL,
        OTHER
    }
}
