import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { CatalogApi } from '../core/api/catalog.api';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';

type Tab = 'strains' | 'bancos';

interface Row {
  readonly id: string;
  readonly name: string;
  readonly subtitle?: string;
  readonly reviewCount: number;
  readonly overall: number;
}

/**
 * Catálogo da comunidade: strains e bancos de sementes, ordenados pelos mais avaliados.
 */
@Component({
  selector: 'app-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <h1 class="text-2xl font-bold">Genéticas</h1>
    <p class="text-fg-muted">Notas dadas por quem cultivou até a colheita.</p>

    <div class="mt-4 flex gap-2" role="tablist" aria-label="Catálogo">
      @for (option of tabs; track option.id) {
        <button
          type="button"
          role="tab"
          class="min-h-11 rounded-full px-4 text-sm font-semibold"
          [class]="tab() === option.id ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
          [attr.aria-selected]="tab() === option.id"
          (click)="navigate({ aba: option.id === 'strains' ? null : option.id, q: null })"
        >
          {{ option.label }}
        </button>
      }
    </div>

    <div class="relative mt-4">
      <label for="catalog-search" class="sr-only">Buscar</label>
      <app-icon name="search" class="pointer-events-none absolute left-3 top-1/2 size-5 -translate-y-1/2 text-fg-muted" />
      <input
        id="catalog-search"
        type="search"
        maxlength="80"
        class="wd-input pl-10 text-base"
        [placeholder]="tab() === 'strains' ? 'Buscar strain' : 'Buscar banco de sementes'"
        [value]="query()"
        (input)="navigate({ q: $any($event.target).value || null })"
      />
    </div>

    @if (result(); as page) {
      @if (page.items.length === 0) {
        <div class="wd-card mt-4 text-center">
          <p class="font-medium">Nada avaliado por aqui ainda.</p>
          <p class="text-sm text-fg-muted">As genéticas entram no catálogo quando alguém conclui um cultivo e publica a review.</p>
        </div>
      }
      <ul class="mt-4 grid gap-2">
        @for (row of page.items; track row.id) {
          <li>
            <a [routerLink]="[tab() === 'strains' ? '/strains' : '/bancos', row.id]" class="wd-card flex items-center gap-3 hover:border-accent/50">
              <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-accent/15 text-accent">
                <app-icon [name]="tab() === 'strains' ? 'leaf' : 'star'" class="size-5" />
              </span>
              <span class="min-w-0 flex-1">
                <span class="block truncate font-semibold">{{ row.name }}</span>
                <span class="block truncate text-sm text-fg-muted">
                  {{ row.subtitle ? row.subtitle + ' · ' : '' }}{{ row.reviewCount }} {{ row.reviewCount === 1 ? 'avaliação' : 'avaliações' }}
                </span>
              </span>
              <span class="inline-flex items-center gap-1 text-lg font-bold tabular-nums">
                <app-icon name="star" class="size-4 text-accent" [filled]="true" /> {{ score(row.overall) }}
              </span>
            </a>
          </li>
        }
      </ul>
    } @else {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);

  readonly aba = input<string | undefined>();
  readonly q = input<string | undefined>();

  protected readonly tabs: readonly { id: Tab; label: string }[] = [
    { id: 'strains', label: 'Strains' },
    { id: 'bancos', label: 'Bancos de sementes' },
  ];
  protected readonly tab = computed<Tab>(() => (this.aba() === 'bancos' ? 'bancos' : 'strains'));
  protected readonly query = computed(() => this.q() ?? '');

  protected readonly result = toSignal(
    toObservable(computed(() => ({ tab: this.tab(), query: this.query() }))).pipe(
      debounceTime(250),
      switchMap(({ tab, query }) => this.load(tab, query).pipe(startWith(null))),
    ),
  );

  protected navigate(params: Record<string, string | null>): void {
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected score(value: number): string {
    return formatDecimal(value, 1);
  }

  private load(tab: Tab, query: string): Observable<Page<Row> | null> {
    const request: Observable<Page<Row>> =
      tab === 'strains'
        ? this.api.strains(query).pipe(
            map((page) => ({ ...page, items: page.items.map((s) => ({ ...s, subtitle: s.breeder })) })),
          )
        : this.api.seedbanks(query);
    return request.pipe(catchError(() => of({ items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 })));
  }
}
