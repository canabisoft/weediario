/**
 * Ranking de growers: modelo e regras de apresentação, sem dependência de Angular.
 * A ordenação e as posições vêm do backend (calculadas sobre todos os growers, antes da busca por nome).
 */

export interface RankedGrower {
  /** Apelido público: a única identidade exibida (nunca nome real). */
  readonly username: string;
  readonly hasAvatar: boolean;
  readonly diaries: number;
  /** Total de reações recebidas nos diários públicos (🔥 🌿 👏). */
  readonly likes: number;
  /** Gramas secas por planta nas colheitas registradas; null se ainda não há colheita. */
  readonly gramsPerPlant: number | null;
}

export type RankingSort = 'diaries' | 'likes' | 'gramsPerPlant';

export const RANKING_SORTS: readonly { id: RankingSort; label: string }[] = [
  { id: 'diaries', label: 'Diários' },
  { id: 'likes', label: 'Curtidas' },
  { id: 'gramsPerPlant', label: 'Gramas por planta' },
];

export interface GrowerLevel {
  readonly name: string;
  readonly minDiaries: number;
}

/** Nível pela quantidade de diários públicos (experiência acumulada), do mais alto para o mais baixo. */
export const GROWER_LEVELS: readonly GrowerLevel[] = [
  { name: 'GrandMaster', minDiaries: 50 },
  { name: 'Guru', minDiaries: 25 },
  { name: 'Mestre', minDiaries: 10 },
  { name: 'Cultivador', minDiaries: 3 },
  { name: 'Iniciante', minDiaries: 0 },
];

export function levelFor(diaries: number): GrowerLevel {
  return GROWER_LEVELS.find((level) => diaries >= level.minDiaries) ?? GROWER_LEVELS[GROWER_LEVELS.length - 1];
}

export interface LevelProgress {
  readonly next: GrowerLevel;
  /** Diários públicos que faltam para o próximo nível. */
  readonly missing: number;
  /** Andamento entre o nível atual e o próximo, de 0 a 1. */
  readonly progress: number;
}

/** Quanto falta para o próximo nível; nulo para quem já é GrandMaster. */
export function nextLevel(diaries: number): LevelProgress | null {
  const current = levelFor(diaries);
  const next = [...GROWER_LEVELS].reverse().find((level) => level.minDiaries > diaries);
  if (!next) {
    return null;
  }
  const span = next.minDiaries - current.minDiaries;
  return { next, missing: next.minDiaries - diaries, progress: (diaries - current.minDiaries) / span };
}

export interface RankingEntry {
  readonly position: number;
  readonly grower: RankedGrower;
  readonly level: GrowerLevel;
}

export interface RankingResult {
  readonly entries: RankingEntry[];
  readonly total: number;
}

export function initialOf(username: string): string {
  return (username.match(/[\p{L}\p{N}]/u)?.[0] ?? '?').toUpperCase();
}
