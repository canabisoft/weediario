import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { RecipeIngredient, RecipeView, RecipesApi } from '../core/api/recipes.api';
import { AuthService } from '../core/auth/auth.service';
import { formatCompact, formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { APPLICATION_LABEL, KIND_LABEL, STAGE_LABEL, UNIT_LABEL, batchLabel } from './recipe-labels';

@Component({
  selector: 'app-recipe-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <a routerLink="/receitas" class="text-sm text-fg-muted">← Receitas</a>
    @if (state(); as s) {
      @if (s.recipe; as r) {
        <header class="mt-2">
          <p class="flex flex-wrap items-center gap-1.5 text-xs">
            <span class="rounded-full px-2 py-0.5 font-semibold" [class]="r.kind === 'MINERAL' ? 'bg-fg/10 text-fg' : 'bg-accent/15 text-accent'">{{ kind[r.kind] }}</span>
            <span class="rounded-full bg-surface-raised px-2 py-0.5">{{ application[r.application] }}</span>
            @if (r.stage) {
              <span class="rounded-full bg-surface-raised px-2 py-0.5">{{ stage[r.stage] }}</span>
            }
            @if (r.visibility === 'PRIVATE') {
              <span class="inline-flex items-center gap-1 text-fg-muted"><app-icon name="lock" class="size-3" /> Privada</span>
            }
          </p>
          <h1 class="mt-2 text-2xl font-bold">{{ r.name }}</h1>
          <p class="text-sm text-fg-muted">
            por <a [routerLink]="['/growers', r.author]" class="underline">{{ r.author }}</a>
            @if (r.replicatedFrom; as origin) {
              · replicada de <a [routerLink]="['/receitas', origin.id]" class="underline">{{ origin.name }}</a>, de {{ origin.author }}
            }
          </p>
          <p class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-fg-muted">
            <span class="inline-flex items-center gap-1"><app-icon name="userPlus" class="size-4" />{{ r.replications }} {{ r.replications === 1 ? 'replicação' : 'replicações' }}</span>
            <span class="inline-flex items-center gap-1"><app-icon name="leaf" class="size-4" />{{ r.diaries }} {{ r.diaries === 1 ? 'cultivo público' : 'cultivos públicos' }}</span>
          </p>
        </header>

        <section class="wd-card mt-4" aria-labelledby="ingredients-title">
          <h2 id="ingredients-title" class="font-semibold">Ingredientes <span class="text-sm font-normal text-fg-muted">{{ batch(r) }}</span></h2>
          <ul class="mt-2 divide-y divide-line">
            @for (ingredient of r.ingredients; track $index) {
              <li class="flex items-baseline justify-between gap-3 py-2">
                <span class="min-w-0">
                  <span class="font-medium">{{ ingredient.name }}</span>
                  @if (ingredient.brand) {
                    <span class="text-sm text-fg-muted"> · {{ ingredient.brand }}</span>
                  }
                  @if (ingredient.productId) {
                    <span class="ml-1 rounded-full bg-accent/15 px-1.5 text-[11px] text-accent">catálogo</span>
                  }
                </span>
                <span class="shrink-0 font-semibold tabular-nums">{{ amount(ingredient) }}</span>
              </li>
            }
          </ul>
          @if (r.targetEc != null || r.targetPh != null) {
            <dl class="mt-3 flex flex-wrap gap-2 text-sm">
              @if (r.targetEc != null) {
                <div class="rounded-lg bg-surface-raised px-3 py-1"><dt class="inline text-fg-muted">EC alvo </dt><dd class="inline font-semibold">{{ num(r.targetEc, 2) }} mS/cm</dd></div>
              }
              @if (r.targetPh != null) {
                <div class="rounded-lg bg-surface-raised px-3 py-1"><dt class="inline text-fg-muted">pH alvo </dt><dd class="inline font-semibold">{{ num(r.targetPh, 1) }}</dd></div>
              }
            </dl>
          }
        </section>

        @if (r.notes) {
          <section class="wd-card mt-4">
            <h2 class="font-semibold">Modo de preparo e observações</h2>
            <p class="mt-2 whitespace-pre-line break-words">{{ r.notes }}</p>
          </section>
        }

        <div class="mt-4 flex flex-wrap gap-2">
          @if (s.mine) {
            <a [routerLink]="['/receitas', r.id, 'editar']" class="wd-button"><app-icon name="edit" class="size-5" /> Editar</a>
            <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="remove(r)">
              <app-icon name="trash" class="size-5" /> Apagar
            </button>
          } @else if (auth.isLoggedIn()) {
            <button type="button" class="wd-button" [disabled]="busy()" (click)="replicate(r)">
              <app-icon name="userPlus" class="size-5" /> Replicar nas minhas receitas
            </button>
          } @else {
            <a routerLink="/entrar" class="wd-button bg-surface-raised text-fg">Entre para replicar esta receita</a>
          }
        </div>
        @if (message()) {
          <p role="status" class="mt-2 text-sm text-danger">{{ message() }}</p>
        }
      } @else {
        <p class="wd-card mt-4">{{ s.error }}</p>
      }
    } @else {
      <div class="mt-4 h-60 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class RecipeDetailPage {
  private readonly api = inject(RecipesApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly kind = KIND_LABEL;
  protected readonly application = APPLICATION_LABEL;
  protected readonly stage = STAGE_LABEL;
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly state = toSignal(
    combineLatest([toObservable(computed(() => this.id())), this.reload$]).pipe(
      switchMap(([id]) =>
        this.api.get(id).pipe(
          map(({ recipe, ownedByViewer }) => ({ recipe, mine: ownedByViewer, error: null })),
          catchError((error) => of({ recipe: null, mine: false, error: problemMessage(error, 'Receita não encontrada.') })),
        ),
      ),
    ),
  );

  protected batch(recipe: RecipeView): string {
    return batchLabel(recipe.application, recipe.batchLiters);
  }

  protected amount(ingredient: RecipeIngredient): string {
    return `${formatCompact(ingredient.quantity, 3)} ${UNIT_LABEL[ingredient.unit]}`;
  }

  protected num(value: number, decimals: number): string {
    return formatDecimal(value, decimals);
  }

  /** A cópia nasce privada: abre direto na edição para o grower ajustar e decidir se publica. */
  protected replicate(recipe: RecipeView): void {
    this.busy.set(true);
    this.api.replicate(recipe.id).subscribe({
      next: (copy) => this.router.navigate(['/receitas', copy.id, 'editar']),
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected remove(recipe: RecipeView): void {
    if (!confirm(`Apagar a receita "${recipe.name}"? Cópias que outros growers fizeram continuam existindo.`)) {
      return;
    }
    this.busy.set(true);
    this.api.delete(recipe.id).subscribe({
      next: () => this.router.navigate(['/receitas'], { queryParams: { aba: 'minhas' } }),
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
