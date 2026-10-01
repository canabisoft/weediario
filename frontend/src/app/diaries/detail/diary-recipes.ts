import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GrowMedium } from '../../core/api/api.models';
import { problemMessage } from '../../core/api/problem';
import { RecipeSummary, RecipesApi } from '../../core/api/recipes.api';
import { Icon } from '../../shared/icon';
import { RecipeCard } from '../../recipes/recipe-card';
import { RecommendedRecipes } from '../../recipes/recommended-recipes';

const MAX_RECIPES = 5;

/**
 * Receitas de nutrição usadas no cultivo. Em cultivo mineral, convida o grower a registrar a receita de sais que
 * está usando; em qualquer meio dá para vincular uma receita própria ou uma pública da comunidade.
 */
@Component({
  selector: 'app-diary-recipes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, RecipeCard, RecommendedRecipes],
  template: `
    <h2 class="font-semibold">Receitas de nutrição</h2>

    @if (recipes().length) {
      <ul class="mt-3 grid gap-2 sm:grid-cols-2">
        @for (recipe of recipes(); track recipe.id) {
          <li class="relative">
            <app-recipe-card [recipe]="recipe" />
            @if (isOwner()) {
              <button type="button" class="absolute bottom-2 right-2 grid size-9 place-items-center rounded-lg bg-surface text-fg-muted"
                      [attr.aria-label]="'Tirar ' + recipe.name + ' do cultivo'" [disabled]="busy()" (click)="unlink(recipe)">
                <app-icon name="close" class="size-4" />
              </button>
            }
          </li>
        }
      </ul>
    } @else if (!isOwner()) {
      <p class="mt-2 text-sm text-fg-muted">Nenhuma receita compartilhada neste cultivo.</p>
    }

    @if (isOwner()) {
      @if (!recipes().length) {
        <p class="mt-2 text-sm" [class.text-fg-muted]="medium() !== 'MINERAL'">
          @if (medium() === 'MINERAL') {
            Cultivo mineral: registre a receita de sais que você está usando (quantidades por litro, EC e pH). Ela ajuda
            quem quiser repetir o seu resultado.
          } @else {
            Registre os insumos e as quantidades que você usa (super solo, chás, coberturas).
          }
        </p>
      }
      <div class="mt-3 flex flex-wrap items-end gap-2">
        @if (available().length && recipes().length < max) {
          <div class="min-w-48 flex-1">
            <label class="wd-label" for="diary-recipe-pick">Usar uma das minhas receitas</label>
            <select id="diary-recipe-pick" class="wd-input" [disabled]="busy()" (change)="link($any($event.target))">
              <option value="">Escolher…</option>
              @for (recipe of available(); track recipe.id) {
                <option [value]="recipe.id">{{ recipe.name }}</option>
              }
            </select>
          </div>
        }
        @if (recipes().length < max) {
          <a [routerLink]="['/receitas/nova']" [queryParams]="{ diario: diaryId() }" class="wd-button">
            <app-icon name="plus" class="size-5" /> Criar receita
          </a>
        }
        <a routerLink="/receitas" [queryParams]="{ tipo: medium() === 'MINERAL' ? 'MINERAL' : null }" class="wd-button bg-surface-raised text-fg">
          Ver receitas da comunidade
        </a>
      </div>
      @if (error()) {
        <p role="alert" class="mt-2 text-sm text-danger">{{ error() }}</p>
      }
      @if (recipes().length < max) {
        <app-recommended-recipes class="mt-5" title="Sugestões para este cultivo" [diaryId]="diaryId()" [size]="2"
                                 actionLabel="Usar neste cultivo" [refresh]="refresh()" (picked)="use($event)" />
      }
    }
  `,
})
export class DiaryRecipes {
  private readonly api = inject(RecipesApi);

  readonly diaryId = input.required<string>();
  readonly medium = input<GrowMedium | undefined>();
  readonly isOwner = input(false);

  protected readonly max = MAX_RECIPES;
  protected readonly recipes = signal<RecipeSummary[]>([]);
  private readonly mine = signal<RecipeSummary[]>([]);
  protected readonly available = computed(() => {
    const linked = new Set(this.recipes().map((recipe) => recipe.id));
    return this.mine().filter((recipe) => !linked.has(recipe.id));
  });
  protected readonly busy = signal(false);
  protected readonly refresh = signal(0);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const diaryId = this.diaryId();
      const owner = this.isOwner();
      untracked(() => {
        this.api.ofDiary(diaryId).subscribe({ next: (recipes) => this.recipes.set(recipes), error: () => this.recipes.set([]) });
        if (owner) {
          this.api.mine().subscribe({ next: (page) => this.mine.set(page.items), error: () => this.mine.set([]) });
        }
      });
    });
  }

  /** Receita sugerida da comunidade: entra no cultivo como está (para ajustar, o caminho é replicar). */
  protected use(recipe: RecipeSummary): void {
    this.save([...this.recipes().map((r) => r.id), recipe.id]);
  }

  protected link(select: HTMLSelectElement): void {
    const id = select.value;
    select.value = '';
    if (id) {
      this.save([...this.recipes().map((recipe) => recipe.id), id]);
    }
  }

  protected unlink(recipe: RecipeSummary): void {
    this.save(this.recipes().filter((r) => r.id !== recipe.id).map((r) => r.id));
  }

  private save(ids: string[]): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.setDiaryRecipes(this.diaryId(), ids).subscribe({
      next: (recipes) => {
        this.recipes.set(recipes);
        this.refresh.update((n) => n + 1);
        this.busy.set(false);
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
