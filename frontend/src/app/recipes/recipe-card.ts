import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RecipeSummary } from '../core/api/recipes.api';
import { Icon } from '../shared/icon';
import { APPLICATION_LABEL, KIND_LABEL, STAGE_LABEL, batchLabel } from './recipe-labels';

@Component({
  selector: 'app-recipe-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  host: { class: 'block' },
  template: `
    @let r = recipe();
    <a [routerLink]="['/receitas', r.id]" class="wd-card group block h-full hover:border-accent/50">
      <p class="flex flex-wrap items-center gap-1.5 text-xs">
        <span class="rounded-full px-2 py-0.5 font-semibold"
              [class]="r.kind === 'MINERAL' ? 'bg-fg/10 text-fg' : 'bg-accent/15 text-accent'">
          {{ kind[r.kind] }}
        </span>
        <span class="rounded-full bg-surface-raised px-2 py-0.5">{{ application[r.application] }}</span>
        @if (r.stage) {
          <span class="rounded-full bg-surface-raised px-2 py-0.5">{{ stage[r.stage] }}</span>
        }
        @if (r.visibility === 'PRIVATE') {
          <span class="ml-auto inline-flex items-center gap-1 text-fg-muted"><app-icon name="lock" class="size-3" /> Privada</span>
        }
      </p>
      <p class="mt-2 truncate font-semibold group-hover:text-accent">{{ r.name }}</p>
      <p class="text-xs text-fg-muted">{{ batch(r) }} · {{ r.ingredientCount }} {{ r.ingredientCount === 1 ? 'ingrediente' : 'ingredientes' }}</p>
      <p class="mt-1 line-clamp-2 text-sm text-fg-muted">
        {{ r.ingredientNames.join(', ') }}{{ r.ingredientCount > r.ingredientNames.length ? '…' : '' }}
      </p>
      <p class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
        @if (showAuthor()) {
          <span>por {{ r.author }}</span>
        }
        <span class="inline-flex items-center gap-1" title="Replicações"><app-icon name="userPlus" class="size-3.5" />{{ r.replications }}</span>
        <span class="inline-flex items-center gap-1" title="Cultivos públicos usando"><app-icon name="leaf" class="size-3.5" />{{ r.diaries }}</span>
      </p>
    </a>
  `,
})
export class RecipeCard {
  readonly recipe = input.required<RecipeSummary>();
  readonly showAuthor = input(true);

  protected readonly kind = KIND_LABEL;
  protected readonly application = APPLICATION_LABEL;
  protected readonly stage = STAGE_LABEL;

  protected batch(recipe: RecipeSummary): string {
    return batchLabel(recipe.application, recipe.batchLiters);
  }
}
