import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, debounceTime, of, startWith, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { RecipeKind, RecipeSort, RecipeSummary, RecipesApi } from '../core/api/recipes.api';
import { AuthService } from '../core/auth/auth.service';
import { Icon } from '../shared/icon';
import { RecipeCard } from './recipe-card';

type Tab = 'explorar' | 'minhas';

const SORTS: readonly { id: RecipeSort; label: string }[] = [
  { id: 'RECENT', label: 'Mais recentes' },
  { id: 'MOST_USED', label: 'Mais usadas em cultivos' },
  { id: 'MOST_REPLICATED', label: 'Mais replicadas' },
];

/**
 * Receitas de nutrição da comunidade (mineral e orgânica) e as do próprio grower.
 * Filtros ficam na URL ({@code ?tipo=MINERAL&produto=...}) para dar para compartilhar o link.
 */
@Component({
  selector: 'app-recipes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, RecipeCard],
  template: `
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">Receitas de nutrição</h1>
        <p class="text-sm text-fg-muted">Sais minerais e insumos orgânicos que a comunidade usa, com as quantidades.</p>
      </div>
      @if (auth.isLoggedIn()) {
        <a routerLink="/receitas/nova" class="wd-button"><app-icon name="plus" class="size-5" /> Nova receita</a>
      }
    </header>

    <div class="mt-4 flex gap-2" role="tablist" aria-label="Seções">
      <button type="button" role="tab" class="min-h-11 rounded-full px-4 text-sm font-semibold"
              [class]="tab() === 'explorar' ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
              [attr.aria-selected]="tab() === 'explorar'" (click)="navigate({ aba: null })">Explorar</button>
      @if (auth.isLoggedIn()) {
        <button type="button" role="tab" class="min-h-11 rounded-full px-4 text-sm font-semibold"
                [class]="tab() === 'minhas' ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
                [attr.aria-selected]="tab() === 'minhas'" (click)="navigate({ aba: 'minhas' })">Minhas receitas</button>
      }
    </div>

    @if (tab() === 'explorar') {
      <div class="mt-4 grid gap-3 sm:grid-cols-[1fr_auto_auto]">
        <label class="sr-only" for="recipe-q">Buscar</label>
        <input id="recipe-q" type="search" class="wd-input" placeholder="Buscar por nome, sal ou marca" [value]="q() ?? ''"
               (input)="navigate({ q: $any($event.target).value || null })" />
        <label class="sr-only" for="recipe-kind">Tipo</label>
        <select id="recipe-kind" class="wd-input" (change)="navigate({ tipo: $any($event.target).value || null })">
          <option value="" [selected]="!kind()">Mineral e orgânica</option>
          <option value="MINERAL" [selected]="kind() === 'MINERAL'">Mineral</option>
          <option value="ORGANIC" [selected]="kind() === 'ORGANIC'">Orgânica</option>
        </select>
        <label class="sr-only" for="recipe-sort">Ordenar</label>
        <select id="recipe-sort" class="wd-input" (change)="navigate({ ordem: $any($event.target).value })">
          @for (s of sorts; track s.id) {
            <option [value]="s.id" [selected]="sort() === s.id">{{ s.label }}</option>
          }
        </select>
      </div>
      @if (produto()) {
        <p class="mt-3 inline-flex items-center gap-2 rounded-full bg-accent/15 py-1 pl-3 pr-1 text-sm text-accent">
          Usando {{ nome() || 'o produto escolhido' }}
          <button type="button" class="grid size-7 place-items-center rounded-full" aria-label="Remover filtro de produto"
                  (click)="navigate({ produto: null, nome: null })">
            <app-icon name="close" class="size-3.5" />
          </button>
        </p>
      }
    }

    @if (result(); as page) {
      <ul class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        @for (recipe of page.items; track recipe.id) {
          <li><app-recipe-card [recipe]="recipe" [showAuthor]="tab() === 'explorar'" /></li>
        } @empty {
          <li class="wd-card text-center text-sm text-fg-muted sm:col-span-2 lg:col-span-3">
            @if (tab() === 'minhas') {
              Você ainda não tem receitas. Crie a sua ou replique uma da comunidade.
            } @else {
              Nenhuma receita pública encontrada.
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
export class RecipesPage {
  private readonly api = inject(RecipesApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  readonly aba = input<string | undefined>();
  readonly q = input<string | undefined>();
  readonly tipo = input<string | undefined>();
  readonly ordem = input<string | undefined>();
  readonly produto = input<string | undefined>();
  readonly nome = input<string | undefined>();
  readonly pagina = input<string | undefined>();

  protected readonly sorts = SORTS;
  protected readonly tab = computed<Tab>(() => (this.aba() === 'minhas' && this.auth.isLoggedIn() ? 'minhas' : 'explorar'));
  protected readonly kind = computed(() =>
    this.tipo() === 'MINERAL' || this.tipo() === 'ORGANIC' ? (this.tipo() as RecipeKind) : undefined,
  );
  protected readonly sort = computed<RecipeSort>(() => SORTS.find((s) => s.id === this.ordem())?.id ?? 'RECENT');

  protected readonly result = toSignal(
    toObservable(
      computed(() => ({
        tab: this.tab(),
        q: this.q(),
        kind: this.kind(),
        sort: this.sort(),
        productId: this.produto(),
        page: Number(this.pagina() ?? 0),
      })),
    ).pipe(
      debounceTime(200),
      switchMap((query) => this.load(query).pipe(startWith(null))),
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

  private load(query: {
    tab: Tab;
    q?: string;
    kind?: RecipeKind;
    sort: RecipeSort;
    productId?: string;
    page: number;
  }): Observable<Page<RecipeSummary> | null> {
    const empty: Page<RecipeSummary> = { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };
    const request =
      query.tab === 'minhas'
        ? this.api.mine(query.page)
        : this.api.explore({ q: query.q, kind: query.kind, sort: query.sort, productId: query.productId, page: query.page });
    return request.pipe(catchError(() => of(empty)));
  }
}
