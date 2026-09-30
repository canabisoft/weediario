import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, startWith, switchMap } from 'rxjs';
import { CatalogDbApi, ProductCategory, RankingKind, RankingRow } from '../core/api/catalog-db.api';
import { formatCompact } from '../shared/decimal';
import { CATEGORY_LABEL, options } from '../shared/labels';

/**
 * "Mais usados": strains, breeders, marcas e produtos que mais aparecem nos diários públicos,
 * com quantos growers diferentes usam e a média de g/planta das colheitas registradas.
 */
@Component({
  selector: 'app-most-used-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1 class="text-2xl font-bold">Mais usados</h1>
    <p class="text-fg-muted">O que a comunidade mais cultiva e usa, contado nos diários públicos.</p>

    <div class="mt-4 flex flex-wrap gap-2" role="tablist" aria-label="Tipo de ranking">
      @for (option of kinds; track option.id) {
        <button
          type="button"
          role="tab"
          class="min-h-11 rounded-full px-4 text-sm font-semibold"
          [class]="kind() === option.id ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
          [attr.aria-selected]="kind() === option.id"
          (click)="navigate({ tipo: option.id === 'STRAINS' ? null : option.id, categoria: null })"
        >
          {{ option.label }}
        </button>
      }
    </div>

    @if (kind() === 'PRODUCTS' || kind() === 'BRANDS') {
      <div class="mt-3 sm:w-64">
        <label class="wd-label" for="rank-category">Categoria</label>
        <select id="rank-category" class="wd-input text-base" (change)="navigate({ categoria: $any($event.target).value || null })">
          <option value="">Todas</option>
          @for (o of categoryOptions; track o.value) {
            <option [value]="o.value" [selected]="categoria() === o.value">{{ o.label }}</option>
          }
        </select>
      </div>
    }

    @if (rows(); as page) {
      <ol class="mt-4 grid gap-2">
        @for (row of page.items; track row.id) {
          <li class="wd-card flex items-center gap-3">
            <span class="grid size-9 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums"
                  [class]="row.position <= 3 ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
                  [attr.aria-label]="'Posição ' + row.position">{{ row.position }}</span>
            <span class="min-w-0 flex-1">
              @if (link(row); as target) {
                <a [routerLink]="target" class="block truncate font-semibold hover:text-accent">{{ row.name }}</a>
              } @else {
                <span class="block truncate font-semibold">{{ row.name }}</span>
              }
              <span class="block truncate text-sm text-fg-muted">
                {{ row.parentName ?? '' }}{{ row.parentName && row.category ? ' · ' : '' }}{{ row.category ? category_[row.category] : '' }}
              </span>
            </span>
            <dl class="grid grid-cols-3 gap-2 text-center text-xs">
              <div><dd class="text-base font-bold tabular-nums">{{ row.diaries }}</dd><dt class="text-fg-muted">diários</dt></div>
              <div><dd class="text-base font-bold tabular-nums">{{ row.growers }}</dd><dt class="text-fg-muted">growers</dt></div>
              <div><dd class="text-base font-bold tabular-nums">{{ row.avgGramsPerPlant != null ? gpp(row.avgGramsPerPlant) : '—' }}</dd><dt class="text-fg-muted">g/planta</dt></div>
            </dl>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Ainda não há diários públicos suficientes para este ranking.</li>
        }
      </ol>
    } @else {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class MostUsedPage {
  private readonly api = inject(CatalogDbApi);
  private readonly router = inject(Router);

  readonly tipo = input<string | undefined>();
  readonly categoria = input<ProductCategory | undefined>();

  protected readonly category_ = CATEGORY_LABEL;
  protected readonly categoryOptions = options(CATEGORY_LABEL);
  protected readonly kinds: readonly { id: RankingKind; label: string }[] = [
    { id: 'STRAINS', label: 'Strains' },
    { id: 'BREEDERS', label: 'Breeders' },
    { id: 'BRANDS', label: 'Marcas' },
    { id: 'PRODUCTS', label: 'Produtos' },
  ];
  protected readonly kind = computed<RankingKind>(() =>
    this.kinds.some((k) => k.id === this.tipo()) ? (this.tipo() as RankingKind) : 'STRAINS',
  );

  protected readonly rows = toSignal(
    toObservable(computed(() => ({ kind: this.kind(), category: this.categoria() }))).pipe(
      switchMap(({ kind, category }) =>
        this.api.ranking(kind, kind === 'PRODUCTS' || kind === 'BRANDS' ? category : undefined).pipe(
          catchError(() => of({ items: [] as RankingRow[], page: 0, size: 0, totalItems: 0, totalPages: 0 })),
          startWith(null),
        ),
      ),
    ),
  );

  protected link(row: RankingRow): string[] | null {
    return this.kind() === 'STRAINS' ? ['/strains', row.id] : this.kind() === 'BREEDERS' ? ['/bancos', row.id] : null;
  }

  protected gpp(value: number): string {
    return formatCompact(value);
  }

  protected navigate(params: Record<string, string | null>): void {
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
