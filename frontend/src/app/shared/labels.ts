import {
  DiaryStatus,
  FeedSort,
  FloweringType,
  GrowEnvironment,
  GrowMedium,
  GrowthStage,
  ReactionType,
  SeedSex,
  Visibility,
} from '../core/api/api.models';

/** Textos em português para os códigos que a API trafega. */

export const STAGE_LABEL: Record<GrowthStage, string> = {
  GERMINATION: 'Germinação',
  VEGETATIVE: 'Vegetativo',
  FLOWERING: 'Flora',
  DRYING_CURING: 'Secagem e cura',
};

export const MEDIUM_LABEL: Record<GrowMedium, string> = {
  LIVING_SOIL: 'Solo vivo',
  MINERAL: 'Mineral',
  INERT: 'Inerte (coco, perlita...)',
};

export const FLOWERING_LABEL: Record<FloweringType, string> = {
  PHOTOPERIOD: 'Fotoperíodo',
  AUTOFLOWER: 'Automática',
};

export const SEED_SEX_LABEL: Record<SeedSex, string> = {
  REGULAR: 'Regular',
  FEMINIZED: 'Feminizada',
  CLONE: 'Clone',
};

export const STATUS_LABEL: Record<DiaryStatus, string> = {
  IN_PROGRESS: 'Em andamento',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
};

export const VISIBILITY_LABEL: Record<Visibility, string> = {
  PUBLIC: 'Público',
  PRIVATE: 'Privado',
};

export const ENVIRONMENT_LABEL: Record<GrowEnvironment, string> = {
  INDOOR: 'Indoor',
  OUTDOOR: 'Outdoor',
  GREENHOUSE: 'Estufa',
};

export const FEED_SORT_LABEL: Record<FeedSort, string> = {
  RECENTLY_UPDATED: 'Atualizados recentemente',
  NEWEST: 'Mais novos',
  MOST_REACTED: 'Mais curtidos',
};

export const REACTIONS: readonly { type: ReactionType; emoji: string; label: string }[] = [
  { type: 'FIRE', emoji: '🔥', label: 'Fogo' },
  { type: 'HERB', emoji: '🌿', label: 'Erva' },
  { type: 'CLAP', emoji: '👏', label: 'Aplausos' },
];

export const DOMINANCE_LABEL: Record<'INDICA' | 'INDICA_DOMINANT' | 'HYBRID' | 'SATIVA_DOMINANT' | 'SATIVA', string> = {
  INDICA: 'Indica',
  INDICA_DOMINANT: 'Indica dominante',
  HYBRID: 'Híbrida (50/50)',
  SATIVA_DOMINANT: 'Sativa dominante',
  SATIVA: 'Sativa',
};

export const SPEED_LABEL: Record<'SHORT' | 'MEDIUM' | 'LONG', string> = {
  SHORT: 'Curta (até 55 dias)',
  MEDIUM: 'Média (56 a 90 dias)',
  LONG: 'Longa (91+ dias)',
};

export const SEED_KIND_LABEL: Record<'FEMINIZED' | 'REGULAR', string> = {
  FEMINIZED: 'Feminizada',
  REGULAR: 'Regular',
};

export const CATEGORY_LABEL: Record<'NUTRIENT' | 'ADDITIVE' | 'SUBSTRATE' | 'PEST_CONTROL' | 'OTHER', string> = {
  NUTRIENT: 'Nutriente',
  ADDITIVE: 'Aditivo',
  SUBSTRATE: 'Substrato',
  PEST_CONTROL: 'Controle de pragas',
  OTHER: 'Outro',
};

/** Rótulo da categoria de produto vinda da API como texto. */
export function categoryLabel(category: string | undefined): string {
  return category ? (CATEGORY_LABEL as Record<string, string>)[category] ?? category : '';
}

export type BreederKind = 'SEEDBANK' | 'INDEPENDENT';
export type StrainGeneration = 'F1' | 'F2' | 'F3_PLUS' | 'S1' | 'BX' | 'IBL' | 'POLYHYBRID';

export const BREEDER_KIND_LABEL: Record<BreederKind, string> = {
  SEEDBANK: 'Banco de sementes',
  INDEPENDENT: 'Breeder independente',
};

/** Geração da genética: diz o quanto ela é estável (o que esperar entre uma planta e outra). */
export const GENERATION_LABEL: Record<StrainGeneration, string> = {
  F1: 'F1 (primeiro cruzamento)',
  F2: 'F2',
  F3_PLUS: 'F3 ou mais',
  S1: 'S1 (autocruzamento)',
  BX: 'BX (retrocruzamento)',
  IBL: 'IBL (linhagem estável)',
  POLYHYBRID: 'Poli-híbrido',
};

export const SOURCE_LABEL: Record<'DATASET' | 'COMMUNITY' | 'BREEDER', string> = {
  DATASET: 'Base pública',
  COMMUNITY: 'Comunidade',
  BREEDER: 'Breeder',
};

export function options<T extends string>(labels: Record<T, string>): { value: T; label: string }[] {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }));
}

const dateFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });

/** Datas "yyyy-MM-dd" da API são datas locais do cultivo, não instantes: formata sem fuso. */
export function formatDate(isoDate: string | undefined): string {
  if (!isoDate) {
    return '';
  }
  const [year, month, day] = isoDate.slice(0, 10).split('-').map(Number);
  return dateFormat.format(new Date(year, month - 1, day));
}

export function today(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
