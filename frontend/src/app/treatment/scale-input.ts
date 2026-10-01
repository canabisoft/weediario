import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { SymptomKind } from '../core/api/treatment.api';

let nextId = 0;

/**
 * Nota de um sintoma: escala de 0 (nada) a 10 (o pior possível) como grupo de rádio, ou contagem de eventos.
 * Valor nulo = não respondido (diferente de 0).
 */
@Component({
  selector: 'app-scale-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (kind() === 'SCALE') {
      <fieldset>
        <legend class="mb-1 text-sm font-medium">{{ label() }}</legend>
        <div class="flex flex-wrap gap-1">
          @for (n of scale; track n) {
            <label class="cursor-pointer">
              <input type="radio" class="peer sr-only" [name]="group" [value]="n" [checked]="value() === n" (change)="value.set(n)" />
              <span
                class="grid size-8 place-items-center rounded-lg border border-line text-sm tabular-nums peer-checked:border-accent peer-checked:bg-accent peer-checked:font-bold peer-checked:text-accent-fg peer-focus-visible:outline-2 peer-focus-visible:outline-accent"
                >{{ n }}</span
              >
            </label>
          }
        </div>
        <p class="mt-1 flex justify-between text-xs text-fg-muted"><span>0 = nada</span><span>10 = o pior possível</span></p>
      </fieldset>
    } @else {
      <label class="grid gap-1">
        <span class="text-sm font-medium">{{ label() }} <span class="text-fg-muted">(quantas vezes)</span></span>
        <input type="number" inputmode="numeric" min="0" max="1000" class="wd-input w-32" [value]="value() ?? ''"
               (input)="onCount($any($event.target).value)" />
      </label>
    }
  `,
})
export class ScaleInput {
  readonly label = input.required<string>();
  readonly kind = input<SymptomKind>('SCALE');
  readonly value = model<number | null>(null);

  protected readonly scale = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
  protected readonly group = `scale-${nextId++}`;

  protected onCount(raw: string): void {
    const parsed = Number.parseInt(raw, 10);
    this.value.set(Number.isFinite(parsed) && parsed >= 0 ? Math.min(parsed, 1000) : null);
  }
}
