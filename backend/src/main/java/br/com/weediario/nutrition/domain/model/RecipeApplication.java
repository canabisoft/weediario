package br.com.weediario.nutrition.domain.model;

/** Como a receita é aplicada; define a que se refere o rendimento em litros. */
public enum RecipeApplication {
    /** Solução para rega: litros de água. */
    FERTIGATION,
    /** Mistura de substrato (ex.: super solo): litros de substrato. */
    SOIL_MIX,
    /** Chá (ex.: chá aerado de húmus): litros de água. */
    TEA,
    /** Pulverização foliar: litros de água. */
    FOLIAR,
    /** Cobertura sobre o solo (top dress): litros de substrato atendidos. */
    TOP_DRESS
}
