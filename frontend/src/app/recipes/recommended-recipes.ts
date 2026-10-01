import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap } from 'rxjs';
import { RecipeKind, RecipeRecommendation, RecipeSummary, RecipesApi } from '../core/api/recipes.api';
import { RecipeCard } from './recipe-card';

/**
 * Receitas sugeridas: as que usam produtos que o grower já tem aparecem primeiro, depois as mais bem avaliadas
 * pela comunidade. Com {@code diaryId}, o tipo segue o meio do cultivo e dá para usar a receita nele direto.
 */
@Component({
  selector: 'app-recommended-recipes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RecipeCard],
  host: { class: 'block' },
  template: `
    @if (items(); as list) {
      @if (list.length) {
        <h2 class="font-semibold">{{ title() }}</h2>
        <ul class="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          @for (item of list; track item.recipe.id) {
            <li class="grid gap-1">
              <app-recipe-card [recipe]="item.recipe" />
              <p class="flex items-center justify-between gap-2 px-1 text-xs text-fg-muted">
                <span>{{ reason(item) }}</span>
                @if (actionLabel()) {
                  <button type="button" class="font-semibold text-accent" (click)="picked.emit(item.recipe)">{{ actionLabel() }}</button>
                }
              </p>
            </li>
          }
        </ul>
      }
    }
  `,
})
export class RecommendedRecipes {
  private readonly api = inject(RecipesApi);

  readonly title = input('Recomendadas para você');
  readonly diaryId = input<string | undefined>();
  readonly kind = input<RecipeKind | undefined>();
  readonly size = input(3);
  /** Texto do botão de ação (ex.: "Usar neste cultivo"); sem ele, só o card. */
  readonly actionLabel = input<string | undefined>();
  /** Muda para recarregar as sugestões (ex.: depois de vincular uma receita). */
  readonly refresh = input(0);
  readonly picked = output<RecipeSummary>();

  protected readonly items = toSignal(
    toObservable(computed(() => ({ diaryId: this.diaryId(), kind: this.kind(), size: this.size(), refresh: this.refresh() }))).pipe(
      switchMap(({ diaryId, kind, size }) =>
        this.api.recommended({ diaryId, kind, size }).pipe(catchError(() => of<RecipeRecommendation[]>([]))),
      ),
    ),
  );

  protected reason(item: RecipeRecommendation): string {
    if (item.sharedProducts > 0) {
      return item.sharedProducts === 1 ? 'Usa 1 produto que você já tem' : `Usa ${item.sharedProducts} produtos que você já tem`;
    }
    if (item.recipe.rating != null && item.recipe.rating >= 4) {
      return 'Bem avaliada pela comunidade';
    }
    return item.recipe.diaries ? 'Usada em cultivos da comunidade' : 'Receita recente da comunidade';
  }
}
