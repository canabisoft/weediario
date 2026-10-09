import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { CatalogDbApi, CatalogSource, ProductCategory } from '../core/api/catalog-db.api';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { CATEGORY_LABEL, DOMINANCE_LABEL, SOURCE_LABEL, options } from '../shared/labels';

type Tab = 'strains' | 'teste' | 'bancos' | 'produtos';
const TAB_IDS: readonly Tab[] = ['strains', 'teste', 'bancos', 'produtos'];

interface Row {
  readonly id: string;
  readonly link: string[];
  readonly query?: Record<string, string>;
  readonly name: string;
  readonly subtitle: string;
  readonly source: CatalogSource;
  readonly verified: boolean;
  /** Diários públicos que usam o item (sementes em teste mostram o andamento no subtítulo). */
  readonly usage?: number;
  readonly score?: number;
  /** Só produtos: receitas públicas que usam o produto. */
  readonly recipes?: number;
}

/**
 * Catálogo da comunidade: strains, bancos de sementes e produtos. Tudo o que já foi cadastrado (base pública
 * importada + comunidade), com uso nos diários e nota média quando houver reviews. Sementes cadastradas por
 * breeders ficam numa aba própria ("Em teste") até a comunidade validar.
 */
@Component({
  selector: 'app-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">Catálogo</h1>
        <p class="text-fg-muted">Genéticas, bancos e insumos que a comunidade usa.</p>
      </div>
      <nav class="flex flex-wrap gap-x-4 gap-y-1 text-sm font-semibold text-accent" aria-label="Mais do catálogo">
        <a routerLink="/mais-usados">Mais usados →</a>
        <a routerLink="/breeders/programa">Programa de breeders →</a>
        <a routerLink="/dados-abertos">Banco aberto →</a>
      </nav>
    </div>

    <div class="mt-4 flex flex-wrap gap-2" role="tablist" aria-label="Catálogo">
      @for (option of tabs; track option.id) {
        <button
          type="button"
          role="tab"
          class="min-h-11 rounded-full px-4 text-sm font-semibold"
          [class]="tab() === option.id ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
          [attr.aria-selected]="tab() === option.id"
          (click)="navigate({ aba: option.id === 'strains' ? null : option.id, q: null, categoria: null })"
        >
          {{ option.label }}
        </button>
      }
    </div>

    <div class="mt-4 flex flex-col gap-2 sm:flex-row">
      <div class="relative flex-1">
        <label for="catalog-search" class="sr-only">Buscar</label>
        <app-icon name="search" class="pointer-events-none absolute left-3 top-1/2 size-5 -translate-y-1/2 text-fg-muted" />
        <input
          id="catalog-search"
          type="search"
          maxlength="80"
          class="wd-input pl-10 text-base"
          [placeholder]="placeholder()"
          [value]="query()"
          (input)="navigate({ q: $any($event.target).value || null })"
        />
      </div>
      @if (tab() === 'produtos') {
        <label class="sr-only" for="catalog-category">Categoria</label>
        <select id="catalog-category" class="wd-input text-base sm:w-56" (change)="navigate({ categoria: $any($event.target).value || null })">
          <option value="">Todas as categorias</option>
          @for (o of categoryOptions; track o.value) {
            <option [value]="o.value" [selected]="categoria() === o.value">{{ o.label }}</option>
          }
        </select>
      }
    </div>

    @if (result(); as page) {
      <p class="mt-3 text-sm text-fg-muted">{{ page.totalItems }} {{ page.totalItems === 1 ? 'item' : 'itens' }}</p>
      <ul class="mt-2 grid gap-2">
        @for (row of page.items; track row.id) {
          <li>
            <a [routerLink]="row.link" [queryParams]="row.query" class="wd-card flex items-center gap-3 hover:border-accent/50">
              <span class="min-w-0 flex-1">
                <span class="flex items-center gap-2">
                  <span class="truncate font-semibold">{{ row.name }}</span>
                  @if (row.verified) {
                    <app-icon name="check" class="size-4 text-accent" />
                  }
                </span>
                <span class="block truncate text-sm text-fg-muted">{{ row.subtitle }}</span>
                <span class="mt-1 inline-block rounded-full bg-surface-raised px-2 text-[11px] text-fg-muted">{{ source[row.source] }}</span>
              </span>
              <span class="text-right text-sm">
                @if (row.score != null) {
                  <span class="flex items-center justify-end gap-1 font-bold"><app-icon name="star" class="size-4 text-accent" [filled]="true" />{{ num(row.score) }}</span>
                }
                @if (row.usage != null) {
                  <span class="block text-xs text-fg-muted">{{ row.usage }} {{ row.usage === 1 ? 'diário' : 'diários' }}</span>
                }
                @if (row.recipes) {
                  <span class="mt-1 inline-flex items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-[11px] font-semibold text-accent">
                    <app-icon name="flask" class="size-3" /> {{ row.recipes }} {{ row.recipes === 1 ? 'receita' : 'receitas' }}
                  </span>
                }
              </span>
            </a>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">
            @if (tab() === 'teste') {
              Nenhuma semente em teste agora. Breeders cadastram as deles pelo <a routerLink="/breeders/programa" class="text-accent underline">programa de breeders</a>.
            } @else {
              Nada encontrado. Dá para cadastrar ao iniciar um diário ou editar um setup.
            }
          </li>
        }
      </ul>
      @if (page.totalPages > 1) {
        <nav class="mt-4 flex items-center justify-center gap-3" aria-label="Paginação">
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="page.page === 0" (click)="navigate({ pagina: page.page - 1 || null })">Anterior</button>
          <span class="text-sm text-fg-muted">{{ page.page + 1 }} / {{ page.totalPages }}</span>
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="page.page + 1 >= page.totalPages" (click)="navigate({ pagina: page.page + 1 })">Próxima</button>
        </nav>
      }
    } @else {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class CatalogPage {
  private readonly api = inject(CatalogDbApi);
  private readonly router = inject(Router);

  readonly aba = input<string | undefined>();
  readonly q = input<string | undefined>();
  readonly categoria = input<ProductCategory | undefined>();
  readonly pagina = input<string | undefined>();

  protected readonly source = SOURCE_LABEL;
  protected readonly categoryOptions = options(CATEGORY_LABEL);
  protected readonly tabs: readonly { id: Tab; label: string }[] = [
    { id: 'strains', label: 'Strains' },
    { id: 'teste', label: 'Em teste' },
    { id: 'bancos', label: 'Bancos e breeders' },
    { id: 'produtos', label: 'Produtos' },
  ];
  protected readonly tab = computed<Tab>(() => TAB_IDS.find((id) => id === this.aba()) ?? 'strains');
  protected readonly query = computed(() => this.q() ?? '');
  protected readonly placeholder = computed(() =>
    ({
      strains: 'Buscar strain ou breeder',
      teste: 'Buscar semente em teste ou breeder',
      bancos: 'Buscar banco de sementes',
      produtos: 'Buscar produto ou marca',
    })[this.tab()],
  );

  protected readonly result = toSignal(
    toObservable(computed(() => ({ tab: this.tab(), q: this.query(), category: this.categoria(), page: Number(this.pagina() ?? 0) }))).pipe(
      debounceTime(250),
      switchMap(({ tab, q, category, page }) => this.load(tab, q, category, page).pipe(startWith(null))),
    ),
  );

  protected navigate(params: Record<string, string | number | null>): void {
    const resetPage = !('pagina' in params);
    this.router.navigate([], {
      queryParams: resetPage ? { ...params, pagina: null } : params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  private load(tab: Tab, q: string, category: ProductCategory | undefined, page: number): Observable<Page<Row> | null> {
    const empty: Page<Row> = { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };
    let request: Observable<Page<Row>>;
    if (tab === 'strains') {
      request = this.api.strains(q, page).pipe(
        map((result) => ({
          ...result,
          items: result.items.map((s) => ({
            id: s.id,
            link: ['/strains', s.id],
            name: s.name,
            subtitle: [s.breeder ?? 'sem breeder informado', s.dominance ? DOMINANCE_LABEL[s.dominance] : null,
              s.floweringType === 'AUTOFLOWER' ? 'automática' : null].filter(Boolean).join(' · '),
            source: s.source,
            verified: s.status === 'VERIFIED',
            usage: s.diaries,
            score: s.reviewCount ? s.overall : undefined,
          })),
        })),
      );
    } else if (tab === 'teste') {
      request = this.api.testingStrains(q, undefined, page).pipe(
        map((result) => ({
          ...result,
          items: result.items.map((s) => ({
            id: s.id,
            link: ['/strains', s.id],
            name: s.name,
            subtitle: [s.breeder, s.progress
              ? `${s.progress.testers}/${s.progress.testersRequired} cultivadores · ${s.progress.reviews}/${s.progress.reviewsRequired} avaliação`
              : null].filter(Boolean).join(' · '),
            source: 'BREEDER' as const,
            verified: false,
          })),
        })),
      );
    } else if (tab === 'bancos') {
      request = this.api.breeders(q, page).pipe(
        map((result) => ({
          ...result,
          items: result.items.map((b) => ({
            id: b.id,
            link: ['/bancos', b.id],
            name: b.name,
            subtitle: [b.kind === 'INDEPENDENT' ? 'Breeder independente' : null,
              `${b.strains} ${b.strains === 1 ? 'strain' : 'strains'} no catálogo`].filter(Boolean).join(' · '),
            source: b.source,
            verified: b.status === 'VERIFIED',
            usage: b.diaries,
            score: b.reviewCount ? b.overall : undefined,
          })),
        })),
      );
    } else {
      request = this.api.products(q, category, page).pipe(
        map((result) => ({
          ...result,
          items: result.items.map((p) => ({
            id: p.id,
            link: ['/produtos', p.id],
            name: p.name,
            subtitle: `${p.brand} · ${CATEGORY_LABEL[p.category]}`,
            source: p.source,
            verified: p.status === 'VERIFIED',
            usage: p.diaries,
            score: p.reviewCount ? p.rating : undefined,
            recipes: p.recipes,
          })),
        })),
      );
    }
    return request.pipe(catchError(() => of(empty)));
  }
}
