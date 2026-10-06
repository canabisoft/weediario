import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { Avatar } from '../shared/avatar';
import { formatCompact, formatInteger } from '../shared/decimal';
import { Icon, IconName } from '../shared/icon';
import { RANKING_SORTS, RankingEntry, RankingResult, RankingSort } from './grower-ranking';
import { GROWER_RANKING_SOURCE } from './grower-ranking.source';

interface Metric {
  readonly sort: RankingSort;
  readonly icon: IconName;
  readonly label: string;
  readonly value: (entry: RankingEntry) => string;
}

/**
 * Ranking de growers. Busca ({@code ?q=}) e ordenação ({@code ?ordem=}) vivem na URL: a busca global do
 * cabeçalho cai direto aqui, o botão voltar funciona e o link pode ser compartilhado.
 */
@Component({
  selector: 'app-growers-ranking-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, RouterLink, Avatar],
  templateUrl: './growers-ranking.page.html',
})
export class GrowersRankingPage {
  private readonly router = inject(Router);
  private readonly source = inject(GROWER_RANKING_SOURCE);

  /** Preenchidos pelo router a partir dos query params. */
  readonly q = input<string | undefined>();
  readonly ordem = input<string | undefined>();

  protected readonly sorts = RANKING_SORTS;
  protected readonly query = computed(() => this.q() ?? '');
  protected readonly sort = computed<RankingSort>(() => {
    const requested = this.ordem();
    return RANKING_SORTS.some((option) => option.id === requested) ? (requested as RankingSort) : 'diaries';
  });

  /** null enquanto carrega. Busca com pequeno atraso para não consultar a cada tecla. */
  private readonly result = toSignal(
    toObservable(computed(() => ({ sort: this.sort(), query: this.query() }))).pipe(
      debounceTime(200),
      switchMap(({ sort, query }) =>
        this.source.load(sort, query).pipe(
          map((result): RankingResult | null => result),
          startWith(null),
          catchError(() => of<RankingResult>({ entries: [], total: 0 })),
        ),
      ),
    ),
    { initialValue: null },
  );

  protected readonly loading = computed(() => this.result() === null);
  protected readonly entries = computed(() => this.result()?.entries ?? []);
  protected readonly total = computed(() => this.result()?.total ?? 0);

  protected readonly metrics: readonly Metric[] = [
    {
      sort: 'gramsPerPlant',
      icon: 'scale',
      label: 'g/planta',
      value: (entry) => (entry.grower.gramsPerPlant === null ? '—' : formatCompact(entry.grower.gramsPerPlant)),
    },
    { sort: 'diaries', icon: 'diaries', label: 'Diários', value: (entry) => formatInteger(entry.grower.diaries) },
    { sort: 'likes', icon: 'flame', label: 'Curtidas', value: (entry) => formatInteger(entry.grower.likes) },
    { sort: 'reviews', icon: 'star', label: 'Avaliações', value: (entry) => formatInteger(entry.grower.reviews) },
  ];

  protected onSearch(value: string): void {
    this.updateUrl({ q: value.trim() || null });
  }

  protected onSort(value: string): void {
    this.updateUrl({ ordem: value === 'diaries' ? null : value });
  }

  protected isTopLevel(entry: RankingEntry): boolean {
    return entry.level.name === 'GrandMaster' || entry.level.name === 'Guru';
  }

  private updateUrl(params: Record<string, string | null>): void {
    // replaceUrl: digitar na busca não enche o histórico com uma entrada por tecla.
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
