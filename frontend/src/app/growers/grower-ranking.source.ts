import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { RankingSortDto, SocialApi } from '../core/api/social.api';
import { RankingResult, RankingSort, levelFor } from './grower-ranking';

/**
 * De onde vem o ranking. A página depende só desta interface (facilita testes e troca de fonte).
 */
export interface GrowerRankingSource {
  load(sort: RankingSort, query: string): Observable<RankingResult>;
}

export const GROWER_RANKING_SOURCE = new InjectionToken<GrowerRankingSource>('GrowerRankingSource');

const SORT_TO_API: Record<RankingSort, RankingSortDto> = {
  diaries: 'DIARIES',
  likes: 'LIKES',
  gramsPerPlant: 'GRAMS_PER_PLANT',
};

/** Ranking do backend: {@code GET /api/v1/growers/ranking} (top 50 para o critério escolhido). */
@Injectable()
export class HttpGrowerRankingSource implements GrowerRankingSource {
  private readonly api = inject(SocialApi);

  load(sort: RankingSort, query: string): Observable<RankingResult> {
    return this.api.ranking(SORT_TO_API[sort], query).pipe(
      map((page) => ({
        total: page.totalItems,
        entries: page.items.map((item) => ({
          position: item.position,
          level: levelFor(item.diaries),
          grower: {
            username: item.username,
            hasAvatar: item.hasAvatar,
            diaries: item.diaries,
            likes: item.likes,
            gramsPerPlant: item.gramsPerPlant ?? null,
          },
        })),
      })),
    );
  }
}
