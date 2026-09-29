import { InjectionToken } from '@angular/core';
import { Observable, of } from 'rxjs';
import { RankedGrower } from './grower-ranking';

/**
 * De onde vem o ranking. A página depende só desta interface, então trocar a fonte de exemplo por
 * uma chamada HTTP ao backend não exige mexer no componente.
 */
export interface GrowerRankingSource {
  load(): Observable<readonly RankedGrower[]>;
}

export const GROWER_RANKING_SOURCE = new InjectionToken<GrowerRankingSource>('GrowerRankingSource');

/**
 * Dados de exemplo enquanto o backend não expõe {@code GET /api/v1/growers/ranking}.
 * Apelidos fictícios; nenhum dado real.
 */
export class SampleGrowerRankingSource implements GrowerRankingSource {
  load(): Observable<readonly RankedGrower[]> {
    return of(SAMPLE_GROWERS);
  }
}

const SAMPLE_GROWERS: readonly RankedGrower[] = [
  { username: 'verdejante', diaries: 64, likes: 12840, gramsPerPlant: 142 },
  { username: 'solo_vivo_sp', diaries: 51, likes: 9310, gramsPerPlant: 188 },
  { username: 'tricoma.lab', diaries: 38, likes: 15020, gramsPerPlant: 121 },
  { username: 'lampada_fria', diaries: 33, likes: 6120, gramsPerPlant: 97 },
  { username: 'kaya_grow', diaries: 27, likes: 7045, gramsPerPlant: 210 },
  { username: 'autoflor_br', diaries: 26, likes: 3380, gramsPerPlant: 64 },
  { username: 'terra.preta', diaries: 19, likes: 4410, gramsPerPlant: 156 },
  { username: 'quantum240', diaries: 15, likes: 2275, gramsPerPlant: 118 },
  { username: 'baixo_estresse', diaries: 12, likes: 1990, gramsPerPlant: 133 },
  { username: 'coco_e_perlita', diaries: 10, likes: 2640, gramsPerPlant: 101 },
  { username: 'semana9', diaries: 8, likes: 1210, gramsPerPlant: 88 },
  { username: 'ipê_roxo', diaries: 6, likes: 1530, gramsPerPlant: null },
  { username: 'primeira_colheita', diaries: 3, likes: 480, gramsPerPlant: 45 },
  { username: 'broto_novo', diaries: 1, likes: 95, gramsPerPlant: null },
];
