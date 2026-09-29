import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatDecimal, parseDecimal } from '../../shared/decimal';
import { EC_GUIDELINES, EC_UNITS, EcUnit, convertToAll } from './ec';

@Component({
  selector: 'app-ec-converter-page',
  imports: [RouterLink],
  template: `
    <a routerLink="/ferramentas" class="text-sm text-fg-muted">← Ferramentas</a>
    <h1 class="mt-1 text-2xl font-bold">Conversor EC / PPM</h1>
    <p class="mt-1 text-sm text-fg-muted">
      PPM é uma estimativa calculada pelo medidor a partir da EC, e cada fabricante usa um fator. Confira no manual qual
      escala o seu usa antes de seguir uma tabela de nutrientes.
    </p>

    <form class="wd-card mt-4 grid grid-cols-[1fr_auto] gap-3" (submit)="$event.preventDefault()">
      <div>
        <label class="wd-label" for="ec-value">Valor medido</label>
        <input
          id="ec-value"
          class="wd-input"
          inputmode="decimal"
          autocomplete="off"
          [value]="value()"
          (input)="value.set($any($event.target).value)"
        />
      </div>
      <div>
        <label class="wd-label" for="ec-unit">Escala</label>
        <select id="ec-unit" class="wd-input" [value]="unit()" (change)="unit.set($any($event.target).value)">
          @for (option of units; track option.id) {
            <option [value]="option.id">{{ option.label }}</option>
          }
        </select>
      </div>
    </form>

    <section class="wd-card mt-4" aria-live="polite">
      @if (conversions(); as all) {
        <ul class="divide-y divide-line">
          @for (option of units; track option.id) {
            <li class="flex items-baseline justify-between gap-3 py-2" [class.text-accent]="option.id === unit()">
              <span>
                <span class="font-medium">{{ option.label }}</span>
                <span class="block text-xs text-fg-muted">{{ option.hint }}</span>
              </span>
              <span class="text-xl font-semibold tabular-nums">{{ fmt(all[option.id], option.decimals) }}</span>
            </li>
          }
        </ul>
      } @else {
        <p class="text-fg-muted">{{ problem() }}</p>
      }
    </section>

    <section class="mt-6">
      <h2 class="text-lg font-semibold">Faixas de referência (mineral / inerte)</h2>
      <div class="mt-2 overflow-hidden rounded-xl border border-line">
        <table class="w-full text-sm tabular-nums">
          <thead class="bg-surface-raised text-left text-fg-muted">
            <tr>
              <th scope="col" class="px-3 py-2">Fase</th>
              <th scope="col" class="px-3 py-2 text-right">EC (mS/cm)</th>
              <th scope="col" class="px-3 py-2 text-right">PPM 500</th>
              <th scope="col" class="px-3 py-2 text-right">PPM 700</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-line">
            @for (row of guidelines; track row.stage) {
              <tr>
                <th scope="row" class="px-3 py-2 text-left font-medium">{{ row.stage }}</th>
                <td class="px-3 py-2 text-right">{{ fmt(row.minMsCm, 1) }}–{{ fmt(row.maxMsCm, 1) }}</td>
                <td class="px-3 py-2 text-right">{{ row.minMsCm * 500 }}–{{ row.maxMsCm * 500 }}</td>
                <td class="px-3 py-2 text-right">{{ row.minMsCm * 700 }}–{{ row.maxMsCm * 700 }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <p class="mt-2 text-xs text-fg-muted">
        Ponto de partida geral: siga a tabela do fabricante do seu nutriente e observe a planta. Em solo vivo a
        nutrição vem da biologia do solo, e a EC da rega não é o principal parâmetro.
      </p>
    </section>
  `,
})
export class EcConverterPage {
  protected readonly units = EC_UNITS;
  protected readonly guidelines = EC_GUIDELINES;

  protected readonly value = signal('1,2');
  protected readonly unit = signal<EcUnit>('ec');

  private readonly evaluation = computed<Record<EcUnit, number> | string>(() => {
    const parsed = parseDecimal(this.value());
    if (parsed === null) {
      return 'Digite o valor que aparece no medidor.';
    }
    try {
      return convertToAll(parsed, this.unit());
    } catch (error) {
      return error instanceof RangeError ? error.message : 'Valor inválido.';
    }
  });

  protected readonly conversions = computed(() => {
    const value = this.evaluation();
    return typeof value === 'string' ? null : value;
  });

  protected readonly problem = computed(() => {
    const value = this.evaluation();
    return typeof value === 'string' ? value : null;
  });

  protected fmt(value: number, decimals: number): string {
    return formatDecimal(value, decimals);
  }
}
