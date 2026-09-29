/**
 * Ranking de growers: modelo e regras de ordenação/filtro, sem dependência de Angular.
 */

export interface RankedGrower {
  /** Apelido público: a única identidade exibida (nunca nome real). */
  readonly username: string;
  /** Opcional: a plataforma ainda não armazena fotos de perfil; a UI usa a inicial. */
  readonly avatarUrl?: string;
  readonly diaries: number;
  /** Total de reações recebidas nos diários (🔥 🌿 👏). */
  readonly likes: number;
  /** Média de gramas secas por planta nos cultivos concluídos; null se ainda não há colheita registrada. */
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

/** Nível pela quantidade de diários publicados (experiência acumulada), do mais alto para o mais baixo. */
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

export interface RankingEntry {
  readonly position: number;
  readonly grower: RankedGrower;
  readonly level: GrowerLevel;
}

/**
 * Ordena e numera o ranking. A posição reflete o critério escolhido sobre <em>todos</em> os growers;
 * a busca por nome filtra depois, então "#7" continua sendo o 7º mesmo quando é o único resultado.
 * Empates recebem a mesma posição (1, 2, 2, 4).
 */
export function buildRanking(growers: readonly RankedGrower[], sort: RankingSort, query: string): RankingEntry[] {
  const metric = (grower: RankedGrower) => grower[sort] ?? -1;
  const sorted = [...growers].sort(
    (a, b) => metric(b) - metric(a) || a.username.localeCompare(b.username, 'pt-BR'),
  );

  let previousValue: number | null = null;
  let previousPosition = 0;
  const ranked = sorted.map((grower, index) => {
    const value = metric(grower);
    const position = value === previousValue ? previousPosition : index + 1;
    previousValue = value;
    previousPosition = position;
    return { position, grower, level: levelFor(grower.diaries) };
  });

  const normalizedQuery = normalize(query);
  return normalizedQuery ? ranked.filter((entry) => normalize(entry.grower.username).includes(normalizedQuery)) : ranked;
}

export function initialOf(username: string): string {
  return (username.match(/[\p{L}\p{N}]/u)?.[0] ?? '?').toUpperCase();
}

function normalize(text: string): string {
  return text.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/\p{M}/gu, '');
}
