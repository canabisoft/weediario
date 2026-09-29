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
