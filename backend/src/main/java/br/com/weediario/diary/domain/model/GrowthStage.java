package br.com.weediario.diary.domain.model;

public enum GrowthStage {
    GERMINATION,
    VEGETATIVE,
    FLOWERING,
    DRYING_CURING;

    /** Clones já começam na fase vegetativa. */
    public static GrowthStage initialFor(SeedSex seedSex) {
        return seedSex == SeedSex.CLONE ? VEGETATIVE : GERMINATION;
    }
}
