import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import { problemMessage } from '../core/api/problem';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { RatingBar } from '../shared/rating';

export type CatalogKind = 'strain' | 'seedbank';

interface Detail {
  readonly name: string;
  readonly subtitle?: string;
  readonly reviewCount: number;
  readonly overall: number;
  readonly criteria: { label: string; value: number }[];
  readonly reviews: {
    id: string;
    author: string;
    diaryId?: string;
    context?: string;
    scores: { label: string; value: number }[];
    report: string;
    createdAt: string;
  }[];
}

/**
 * Detalhe de uma strain ou de um banco de sementes: médias por critério e as avaliações publicadas.
 * O tipo vem do {@code data} da rota.
 */
@Component({
  selector: 'app-catalog-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, RatingBar],
  template: `
    <a routerLink="/strains" [queryParams]="kind() === 'seedbank' ? { aba: 'bancos' } : {}" class="text-sm text-fg-muted">
      ← {{ kind() === 'strain' ? 'Strains' : 'Bancos de sementes' }}
    </a>
    @if (detail(); as state) {
      @if (state.detail; as d) {
        <header class="mt-2 flex items-start justify-between gap-4">
          <div>
            <h1 class="text-2xl font-bold">{{ d.name }}</h1>
            @if (d.subtitle) {
              <p class="text-fg-muted">{{ d.subtitle }}</p>
            }
            <p class="text-sm text-fg-muted">{{ d.reviewCount }} {{ d.reviewCount === 1 ? 'avaliação' : 'avaliações' }}</p>
          </div>
          <p class="inline-flex items-center gap-1 text-3xl font-bold tabular-nums">
            <app-icon name="star" class="size-6 text-accent" [filled]="true" />{{ num(d.overall) }}
          </p>
        </header>

        <section class="wd-card mt-4 grid gap-2" aria-label="Médias por critério">
          @for (criterion of d.criteria; track criterion.label) {
            <app-rating-bar [label]="criterion.label" [value]="criterion.value" />
          }
        </section>

        <h2 class="mt-6 text-lg font-semibold">Avaliações</h2>
        <ul class="mt-3 grid gap-3">
          @for (review of d.reviews; track review.id) {
            <li class="wd-card">
              <p class="flex flex-wrap items-center gap-2 text-sm">
                <a [routerLink]="['/growers', review.author]" class="font-semibold">{{ review.author }}</a>
                @if (review.context) {
                  <span class="text-fg-muted">· {{ review.context }}</span>
                }
                @if (review.diaryId) {
                  <a [routerLink]="['/diarios', review.diaryId]" class="ml-auto text-xs text-accent">Ver o diário</a>
                }
              </p>
              <p class="mt-1 flex flex-wrap gap-x-3 text-xs text-fg-muted">
                @for (score of review.scores; track score.label) {
                  <span>{{ score.label }} <strong class="text-fg">{{ score.value }}</strong></span>
                }
              </p>
              <p class="mt-2 whitespace-pre-line break-words">{{ review.report }}</p>
            </li>
          }
        </ul>
      } @else {
        <p class="wd-card mt-4">{{ state.error }}</p>
      }
    } @else {
      <div class="mt-4 h-60 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class CatalogDetailPage {
  private readonly api = inject(CatalogApi);

  /** Parâmetro de rota e tipo (data da rota). */
  readonly id = input.required<string>();
  readonly kind = input<CatalogKind>('strain');

  protected readonly detail = toSignal(
    toObservable(computed(() => ({ id: this.id(), kind: this.kind() }))).pipe(
      switchMap(({ id, kind }) =>
        (kind === 'strain' ? this.strain(id) : this.seedbank(id)).pipe(
          map((detail) => ({ detail, error: null })),
          catchError((error) => of({ detail: null, error: problemMessage(error, 'Não encontrado.') })),
        ),
      ),
    ),
  );

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  private strain(id: string) {
    return forkJoin([this.api.strain(id), this.api.strainReviews(id)]).pipe(
      map(([s, reviews]): Detail => ({
        name: s.name,
        subtitle: s.breeder,
        reviewCount: s.reviewCount,
        overall: s.overall,
        criteria: [
          { label: 'Vigor vegetativo', value: s.vigor },
          { label: 'Resistência', value: s.pestResistance },
          { label: 'Facilidade', value: s.easeOfGrowth },
          { label: 'Rendimento', value: s.yield },
          { label: 'Aroma e sabor', value: s.terpeneProfile },
        ],
        reviews: reviews.items.map((r) => ({
          ...r,
          scores: [
            { label: 'Vigor', value: r.vigor },
            { label: 'Resistência', value: r.pestResistance },
            { label: 'Facilidade', value: r.easeOfGrowth },
            { label: 'Rendimento', value: r.yield },
            { label: 'Aroma', value: r.terpeneProfile },
          ],
        })),
      })),
    );
  }

  private seedbank(id: string) {
    return forkJoin([this.api.seedbank(id), this.api.seedbankReviews(id)]).pipe(
      map(([b, reviews]): Detail => ({
        name: b.name,
        reviewCount: b.reviewCount,
        overall: b.overall,
        criteria: [
          { label: 'Germinação', value: b.germination },
          { label: 'Estabilidade genética', value: b.stability },
          { label: 'Atendimento e entrega', value: b.service },
        ],
        reviews: reviews.items.map((r) => ({
          ...r,
          context: r.strain,
          scores: [
            { label: 'Germinação', value: r.germination },
            { label: 'Estabilidade', value: r.stability },
            { label: 'Atendimento', value: r.service },
          ],
        })),
      })),
    );
  }
}
