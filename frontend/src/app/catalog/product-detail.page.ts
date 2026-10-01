import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { CatalogDbApi, ProductListItem, ProductReview } from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { SOURCE_LABEL, categoryLabel, formatDate } from '../shared/labels';

const EMPTY_PAGE: Page<ProductReview> = { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };

interface State {
  readonly product: ProductListItem | null;
  readonly reviews: Page<ProductReview>;
  readonly error: string | null;
}

/**
 * Detalhe de um produto do catálogo (fertilizante, substrato...): marca, categoria, uso nos diários públicos,
 * receitas que o usam e as notas que ele recebeu nas reviews pós-colheita. As notas não mostram quem avaliou e
 * só levam ao diário quando ele é público.
 */
@Component({
  selector: 'app-product-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <a routerLink="/strains" [queryParams]="{ aba: 'produtos' }" class="text-sm text-fg-muted">← Produtos</a>
    @if (state(); as s) {
      @if (s.product; as p) {
        <header class="mt-2 flex items-start justify-between gap-4">
          <div class="min-w-0">
            <h1 class="flex flex-wrap items-center gap-2 text-2xl font-bold">
              <span class="break-words">{{ p.name }}</span>
              @if (p.status === 'VERIFIED') {
                <span class="inline-flex items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-xs font-semibold text-accent">
                  <app-icon name="check" class="size-3" /> verificado
                </span>
              }
            </h1>
            <p class="text-fg-muted">{{ p.brand }} · {{ category(p.category) }}</p>
            <p class="mt-1 text-xs text-fg-muted">Fonte: {{ source[p.source] }}</p>
          </div>
          @if (p.reviewCount && p.rating != null) {
            <p class="inline-flex shrink-0 items-center gap-1 text-3xl font-bold tabular-nums">
              <app-icon name="star" class="size-6 text-accent" [filled]="true" />{{ num(p.rating) }}
            </p>
          }
        </header>

        <dl class="wd-card mt-4 grid grid-cols-3 gap-3 text-sm">
          <div>
            <dt class="text-xs text-fg-muted">Diários públicos</dt>
            <dd class="text-lg font-semibold tabular-nums">{{ p.diaries }}</dd>
          </div>
          <div>
            <dt class="text-xs text-fg-muted">Receitas</dt>
            <dd class="text-lg font-semibold tabular-nums">
              @if (p.recipes) {
                <a routerLink="/receitas" [queryParams]="{ produto: p.id, nome: p.brand + ' ' + p.name }" class="text-accent underline">{{ p.recipes }}</a>
              } @else {
                0
              }
            </dd>
          </div>
          <div>
            <dt class="text-xs text-fg-muted">Avaliações</dt>
            <dd class="text-lg font-semibold tabular-nums">{{ p.reviewCount }}</dd>
          </div>
        </dl>

        <h2 class="mt-6 text-lg font-semibold">Notas nas colheitas</h2>
        <p class="text-sm text-fg-muted">
          Dadas por quem usou o produto num cultivo concluído, junto com a avaliação pós-colheita da strain.
        </p>
        <ul class="mt-3 grid gap-2">
          @for (review of s.reviews.items; track review.id) {
            <li class="wd-card flex flex-wrap items-center gap-x-3 gap-y-1">
              <span class="inline-flex items-center gap-0.5 font-bold tabular-nums" [attr.aria-label]="review.rating + ' de 5 estrelas'">
                @for (star of stars; track star) {
                  <app-icon name="star" class="size-4" [class.text-accent]="star <= review.rating" [class.text-fg-muted]="star > review.rating" [filled]="star <= review.rating" />
                }
              </span>
              <span class="min-w-0 flex-1 text-sm">
                Cultivo de <a [routerLink]="['/strains', review.strainId]" class="font-semibold">{{ review.strain }}</a>
                @if (review.breeder) {
                  <span class="text-fg-muted"> · {{ review.breeder }}</span>
                }
              </span>
              <span class="text-xs text-fg-muted">{{ date(review.createdAt) }}</span>
              @if (review.diaryId) {
                <a [routerLink]="['/diarios', review.diaryId]" class="text-xs text-accent">Ver o diário</a>
              }
            </li>
          } @empty {
            <li class="wd-card text-sm text-fg-muted">
              Ainda sem notas. Elas chegam quando alguém conclui um cultivo com este produto e publica a avaliação.
            </li>
          }
        </ul>
      } @else {
        <p class="wd-card mt-4">{{ s.error }}</p>
      }
    } @else {
      <div class="mt-4 h-60 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class ProductDetailPage {
  private readonly api = inject(CatalogDbApi);

  /** Parâmetro de rota. */
  readonly id = input.required<string>();

  protected readonly source = SOURCE_LABEL;
  protected readonly stars = [1, 2, 3, 4, 5];

  protected readonly state = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        forkJoin([this.api.product(id), this.api.productReviews(id).pipe(catchError(() => of(EMPTY_PAGE)))]).pipe(
          map(([product, reviews]): State => ({ product, reviews, error: null })),
          catchError((error) => of<State>({ product: null, reviews: EMPTY_PAGE, error: problemMessage(error, 'Produto não encontrado.') })),
        ),
      ),
    ),
  );

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  protected category(value: string): string {
    return categoryLabel(value);
  }

  protected date(iso: string): string {
    return formatDate(iso);
  }
}
