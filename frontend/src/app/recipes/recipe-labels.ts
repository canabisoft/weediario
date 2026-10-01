import { IngredientUnit, RecipeApplication, RecipeKind, RecipeStage } from '../core/api/recipes.api';

export const KIND_LABEL: Record<RecipeKind, string> = {
  MINERAL: 'Mineral',
  ORGANIC: 'Orgânica',
};

export const APPLICATION_LABEL: Record<RecipeApplication, string> = {
  FERTIGATION: 'Rega (fertirrigação)',
  SOIL_MIX: 'Mistura de substrato',
  TEA: 'Chá',
  FOLIAR: 'Foliar',
  TOP_DRESS: 'Cobertura (top dress)',
};

export const STAGE_LABEL: Record<RecipeStage, string> = {
  VEGETATIVE: 'Vegetativo',
  FLOWERING: 'Floração',
  ALL: 'Ciclo todo',
};

export const UNIT_LABEL: Record<IngredientUnit, string> = {
  G: 'g',
  MG: 'mg',
  KG: 'kg',
  ML: 'mL',
  L: 'L',
  TSP: 'colher de chá',
  TBSP: 'colher de sopa',
  UNIT: 'unidade',
};

/** Rega, chá e foliar rendem litros de água; mistura e cobertura, litros de substrato. */
export function batchLabel(application: RecipeApplication, liters: number): string {
  const amount = Number(liters).toLocaleString('pt-BR', { maximumFractionDigits: 2 });
  const of = application === 'SOIL_MIX' || application === 'TOP_DRESS' ? 'de substrato' : 'de água';
  return `para ${amount} L ${of}`;
}

export function isSolution(application: RecipeApplication): boolean {
  return application === 'FERTIGATION' || application === 'FOLIAR';
}

/**
 * Pontos de partida para a lista de ingredientes genéricos (sem marca). Os orgânicos vêm do e-book de cultivo
 * indoor (super solo e chá aerado); os minerais são os sais mais comuns em fertirrigação.
 */
export const COMMON_INGREDIENTS: Record<RecipeKind, readonly string[]> = {
  MINERAL: [
    'Nitrato de cálcio',
    'Nitrato de potássio',
    'Fosfato monopotássico (MKP)',
    'Fosfato monoamônico (MAP)',
    'Sulfato de magnésio',
    'Sulfato de potássio',
    'Cloreto de potássio',
    'Micronutrientes quelatados',
    'Ferro quelatado (EDDHA)',
    'Ácido fosfórico (pH-)',
    'Silicato de potássio',
  ],
  ORGANIC: [
    'Húmus de minhoca',
    'Bokashi',
    'Bokashi líquido',
    'Biochar',
    'Azomite (pó de rocha)',
    'Fosfato natural',
    'Farinha de algas',
    'Silicato de potássio',
    'Gesso agrícola',
    'Terra de diatomáceas',
    'Perlita',
    'Casca de arroz',
    'Fibra de coco',
    'Bunny guano',
    'Bat guano',
    'Hidrolisado de peixe',
    'Melaço',
    'Farinha de alfafa',
  ],
};
