import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TestingProgress } from '../core/api/catalog-db.api';

/**
 * Andamento do teste de uma semente de breeder: cultivadores e avaliações que faltam para ela entrar no banco
 * oficial. Um segmento por etapa (sem largura calculada em estilo inline, por causa da CSP).
 */
@Component({
  selector: 'app-testing-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid gap-2 text-sm">
      @for (row of rows(); track row.label) {
        <div>
          <p class="flex justify-between gap-2">
            <span class="text-fg-muted">{{ row.label }}</span>
            <span class="font-semibold tabular-nums">{{ row.done }}/{{ row.required }}</span>
          </p>
          <span class="mt-1 flex gap-1" aria-hidden="true">
            @for (step of row.steps; track $index) {
              <span class="h-2 flex-1 rounded-full" [class]="step ? 'bg-accent' : 'bg-surface-raised'"></span>
            }
          </span>
        </div>
      }
    </div>
  `,
})
export class TestingProgressBar {
  readonly progress = input.required<TestingProgress>();

  protected readonly rows = computed(() => {
    const p = this.progress();
    return [
      row('Colheita e avaliação do breeder', p.breederReviewed ? 1 : 0, 1),
      row('Cultivadores de fora do breeder', p.testers, p.testersRequired),
      row(p.reviewsRequired === 1 ? 'Avaliação pós-colheita' : 'Avaliações pós-colheita', p.reviews, p.reviewsRequired),
    ];
  });
}

function row(label: string, done: number, required: number) {
  const capped = Math.min(done, required);
  return { label, done: capped, required, steps: Array.from({ length: required }, (_, i) => i < capped) };
}
