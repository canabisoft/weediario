import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { formatInteger } from '../shared/decimal';
import { Icon, IconName } from '../shared/icon';
import { RANKING_SORTS, RankingEntry, RankingSort, buildRanking, initialOf } from './grower-ranking';
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
  imports: [Icon],
  templateUrl: './growers-ranking.page.html',
})
export class GrowersRankingPage {
  private readonly router = inject(Router);
  private readonly growers = toSignal(inject(GROWER_RANKING_SOURCE).load());

  /** Preenchidos pelo router a partir dos query params. */
  readonly q = input<string | undefined>();
  readonly ordem = input<string | undefined>();

  protected readonly sorts = RANKING_SORTS;
  protected readonly query = computed(() => this.q() ?? '');
  protected readonly sort = computed<RankingSort>(() => {
    const requested = this.ordem();
    return RANKING_SORTS.some((option) => option.id === requested) ? (requested as RankingSort) : 'diaries';
  });

  protected readonly loading = computed(() => this.growers() === undefined);
  protected readonly entries = computed(() => buildRanking(this.growers() ?? [], this.sort(), this.query()));
  protected readonly total = computed(() => this.growers()?.length ?? 0);

  protected readonly metrics: readonly Metric[] = [
    {
      sort: 'gramsPerPlant',
      icon: 'scale',
      label: 'g/planta',
      value: (entry) => (entry.grower.gramsPerPlant === null ? '—' : formatInteger(entry.grower.gramsPerPlant)),
    },
    { sort: 'diaries', icon: 'diaries', label: 'Diários', value: (entry) => formatInteger(entry.grower.diaries) },
    { sort: 'likes', icon: 'flame', label: 'Curtidas', value: (entry) => formatInteger(entry.grower.likes) },
  ];

  protected onSearch(value: string): void {
    this.updateUrl({ q: value.trim() || null });
  }

  protected onSort(value: string): void {
    this.updateUrl({ ordem: value === 'diaries' ? null : value });
  }

  protected initial(username: string): string {
    return initialOf(username);
  }

  /** Se a foto não carregar, some e o fallback com a inicial aparece no lugar. */
  protected hideBrokenImage(event: Event): void {
    (event.target as HTMLImageElement).hidden = true;
  }

  protected isTopLevel(entry: RankingEntry): boolean {
    return entry.level.name === 'GrandMaster' || entry.level.name === 'Guru';
  }

  private updateUrl(params: Record<string, string | null>): void {
    // replaceUrl: digitar na busca não enche o histórico com uma entrada por tecla.
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
