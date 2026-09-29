import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { formatDecimal } from './decimal';
import { Icon } from './icon';

/**
 * Nota de 1 a 5 em estrelas, como grupo de rádio acessível (setas do teclado funcionam nativamente).
 */
@Component({
  selector: 'app-star-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <fieldset>
      <legend class="wd-label">{{ label() }}</legend>
      <div class="flex gap-1">
        @for (star of stars; track star) {
          <label class="cursor-pointer">
            <input
              type="radio"
              class="peer sr-only"
              [name]="name()"
              [value]="star"
              [checked]="value() === star"
              (change)="value.set(star)"
            />
            <span class="sr-only">{{ star }} de 5</span>
            <app-icon
              name="star"
              class="size-9 rounded-lg p-0.5 peer-focus-visible:outline-2 peer-focus-visible:outline-accent"
              [class.text-accent]="star <= (value() ?? 0)"
              [class.text-fg-muted]="star > (value() ?? 0)"
              [filled]="star <= (value() ?? 0)"
            />
          </label>
        }
      </div>
    </fieldset>
  `,
})
export class StarInput {
  readonly label = input.required<string>();
  readonly name = input.required<string>();
  readonly value = model<number | null>(null);

  protected readonly stars = [1, 2, 3, 4, 5];
}

/** Média com barra horizontal, para as notas agregadas das strains e bancos. */
@Component({
  selector: 'app-rating-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-3 text-sm">
      <span class="w-40 shrink-0 text-fg-muted">{{ label() }}</span>
      <span class="h-2 flex-1 overflow-hidden rounded-full bg-surface-raised" aria-hidden="true">
        <span class="block h-full rounded-full bg-accent" [style.width.%]="percent()"></span>
      </span>
      <span class="w-8 text-right font-semibold tabular-nums">{{ formatted() }}</span>
    </div>
  `,
})
export class RatingBar {
  readonly label = input.required<string>();
  readonly value = input.required<number>();

  protected readonly percent = computed(() => (this.value() / 5) * 100);
  protected readonly formatted = computed(() => formatDecimal(this.value(), 1));
}
