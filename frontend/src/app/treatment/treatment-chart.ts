import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { DayPoint, Series, Symptom } from '../core/api/treatment.api';
import { formatCompact } from '../shared/decimal';

let nextId = 0;

const MARGIN = { left: 40, right: 12, top: 10, bottom: 24 };
const MG_HEIGHT = 170;
const SCALE_HEIGHT = 150;
const COUNT_HEIGHT = 110;
const MAX_SYMPTOMS = 3;

/** Cor fixa por posição do sintoma (classes estáticas para o Tailwind gerar). */
const SYMPTOM_FILL = ['fill-chart-1', 'fill-chart-2', 'fill-chart-3'];
const SYMPTOM_STROKE = ['stroke-chart-1', 'stroke-chart-2', 'stroke-chart-3'];
const SYMPTOM_BG = ['bg-chart-1', 'bg-chart-2', 'bg-chart-3'];
/** Codificação secundária: tracejado e formato do marcador mudam por sintoma, não só a cor. */
const DASH = ['', '6 3', '2 3'];
const SHAPE = ['circle', 'square', 'triangle'] as const;

interface Plotted {
  readonly symptom: Symptom;
  readonly index: number;
}

/**
 * Uso e sintomas ao longo dos dias, em gráficos separados e alinhados pelo mesmo eixo de datas (nunca dois
 * eixos Y num gráfico só): mg/dia de CBD e THC em barras empilhadas, notas médias (0–10) em linhas e
 * contagens (ex.: crises) em barras. Só mostra o que foi registrado; dias sem nota ficam em branco.
 */
@Component({
  selector: 'app-treatment-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @let s = series();
    <!-- Legenda: identidade nunca depende só da cor (swatch + nome; tracejado/forma nas linhas). -->
    <ul class="flex flex-wrap gap-x-4 gap-y-1 text-sm" aria-label="Legenda">
      <li class="flex items-center gap-1.5"><span class="inline-block size-3 rounded-sm bg-chart-cbd"></span>CBD (mg)</li>
      <li class="flex items-center gap-1.5"><span class="inline-block size-3 rounded-sm bg-chart-thc"></span>THC (mg)</li>
      @for (p of plotted(); track p.symptom.id) {
        <li class="flex items-center gap-1.5">
          <span class="inline-block size-3 rounded-full" [class]="bg[p.index]"></span>{{ p.symptom.name }}{{ p.symptom.kind === 'COUNT' ? ' (contagem)' : ' (0–10)' }}
        </li>
      }
    </ul>

    <div
      class="relative mt-2 outline-none focus-visible:ring-2 focus-visible:ring-accent"
      tabindex="0"
      role="group"
      [attr.aria-label]="'Gráficos de ' + s.days.length + ' dias. Use as setas para percorrer os dias.'"
      (keydown)="onKey($event)"
      (mouseleave)="hover.set(null)"
      (blur)="hover.set(null)"
    >
      @if (width() > 0) {
        <!-- 1. mg por dia -->
        <p class="text-xs text-fg-muted">mg por dia (CBD + THC)</p>
        <svg [attr.width]="width()" [attr.height]="mgHeight" class="block overflow-visible" aria-hidden="true">
          <defs>
            <pattern [attr.id]="hatchId" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <rect width="1.6" height="4" class="fill-surface"></rect>
            </pattern>
          </defs>
          @for (tick of mgTicks(); track tick.value) {
            <line [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="tick.y" [attr.y2]="tick.y" class="stroke-line" stroke-width="1"></line>
            <text [attr.x]="left - 6" [attr.y]="tick.y + 4" text-anchor="end" class="fill-fg-muted text-[11px]">{{ tick.label }}</text>
          }
          @for (bar of mgBars(); track bar.day) {
            @if (bar.cbd) {
              <path [attr.d]="bar.cbd" class="fill-chart-cbd"></path>
            }
            @if (bar.thc) {
              <path [attr.d]="bar.thc" class="fill-chart-thc"></path>
              <path [attr.d]="bar.thc" [attr.fill]="'url(#' + hatchId + ')'" class="hidden night:inline"></path>
            }
          }
          <ng-container [ngTemplateOutlet]="hoverLine" [ngTemplateOutletContext]="{ height: mgHeight }" />
          <ng-container [ngTemplateOutlet]="xAxis" [ngTemplateOutletContext]="{ height: mgHeight }" />
        </svg>

        <!-- 2. notas de escala (0–10) -->
        @if (scalePlotted().length) {
          <p class="mt-3 text-xs text-fg-muted">Nota média do dia (0 = nada, 10 = o pior possível)</p>
          <svg [attr.width]="width()" [attr.height]="scaleHeight" class="block overflow-visible" aria-hidden="true">
            @for (tick of scaleTicks(); track tick.value) {
              <line [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="tick.y" [attr.y2]="tick.y" class="stroke-line" stroke-width="1"></line>
              <text [attr.x]="left - 6" [attr.y]="tick.y + 4" text-anchor="end" class="fill-fg-muted text-[11px]">{{ tick.label }}</text>
            }
            @for (line of scaleLines(); track line.id) {
              @for (segment of line.segments; track $index) {
                <path [attr.d]="segment" fill="none" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"
                      [attr.stroke-dasharray]="line.dash || null" [class]="line.stroke"></path>
              }
              @for (point of line.points; track point.x) {
                <path [attr.d]="point.d" [class]="line.fill + ' stroke-surface'" stroke-width="2"></path>
              }
            }
            <ng-container [ngTemplateOutlet]="hoverLine" [ngTemplateOutletContext]="{ height: scaleHeight }" />
            <ng-container [ngTemplateOutlet]="xAxis" [ngTemplateOutletContext]="{ height: scaleHeight }" />
          </svg>
        }

        <!-- 3. contagens (uma por sintoma, cada uma no próprio eixo) -->
        @for (count of countCharts(); track count.id) {
          <p class="mt-3 text-xs text-fg-muted">{{ count.name }} por dia</p>
          <svg [attr.width]="width()" [attr.height]="countHeight" class="block overflow-visible" aria-hidden="true">
            @for (tick of count.ticks; track tick.value) {
              <line [attr.x1]="left" [attr.x2]="width() - right" [attr.y1]="tick.y" [attr.y2]="tick.y" class="stroke-line" stroke-width="1"></line>
              <text [attr.x]="left - 6" [attr.y]="tick.y + 4" text-anchor="end" class="fill-fg-muted text-[11px]">{{ tick.label }}</text>
            }
            @for (bar of count.bars; track bar.day) {
              <path [attr.d]="bar.d" [class]="count.fill"></path>
            }
            <ng-container [ngTemplateOutlet]="hoverLine" [ngTemplateOutletContext]="{ height: countHeight }" />
            <ng-container [ngTemplateOutlet]="xAxis" [ngTemplateOutletContext]="{ height: countHeight }" />
          </svg>
        }

        <!-- Alvos de hover: uma coluna por dia, mais larga que a barra. -->
        <div class="absolute inset-0 flex" [style.padding-left.px]="left" [style.padding-right.px]="right">
          @for (day of s.days; track day.day; let i = $index) {
            <div class="h-full flex-1" (mouseenter)="hover.set(i)"></div>
          }
        </div>

        @if (hovered(); as h) {
          <div
            class="pointer-events-none absolute top-0 z-10 w-48 rounded-xl border border-line bg-surface p-2 text-xs shadow-lg"
            [style.left.px]="tooltipLeft()"
            role="status"
          >
            <p class="font-semibold text-fg">{{ dayLabel(h.day.day) }}</p>
            <p class="text-fg-muted">{{ h.day.doses }} {{ h.day.doses === 1 ? 'uso' : 'usos' }}</p>
            <p class="flex items-center gap-1.5 text-fg"><span class="inline-block size-2.5 rounded-sm bg-chart-cbd"></span>CBD {{ mgText(h.day.cbdMg) }}</p>
            <p class="flex items-center gap-1.5 text-fg"><span class="inline-block size-2.5 rounded-sm bg-chart-thc"></span>THC {{ mgText(h.day.thcMg) }}</p>
            @for (p of plotted(); track p.symptom.id) {
              <p class="flex items-center gap-1.5 text-fg">
                <span class="inline-block size-2.5 rounded-full" [class]="bg[p.index]"></span>
                {{ p.symptom.name }}: {{ symptomValue(h.day, p.symptom.id) }}
              </p>
            }
          </div>
        }
      }
    </div>

    <ng-template #xAxis let-height="height">
      @for (label of xLabels(); track label.x) {
        <svg:text [attr.x]="label.x" [attr.y]="height - 6" text-anchor="middle" class="fill-fg-muted text-[11px]">{{ label.text }}</svg:text>
      }
    </ng-template>

    <ng-template #hoverLine let-height="height">
      @if (hoverX(); as x) {
        <svg:line [attr.x1]="x" [attr.x2]="x" [attr.y1]="top" [attr.y2]="height - bottom" class="stroke-fg-muted" stroke-width="1" stroke-dasharray="3 3"></svg:line>
      }
    </ng-template>

    <details class="mt-3 text-sm">
      <summary class="cursor-pointer text-fg-muted">Ver os dados em tabela</summary>
      <div class="mt-2 overflow-x-auto">
        <table class="w-full text-left tabular-nums">
          <thead>
            <tr class="border-b border-line text-fg-muted">
              <th class="py-1 pr-3 font-medium">Dia</th>
              <th class="py-1 pr-3 font-medium">Usos</th>
              <th class="py-1 pr-3 font-medium">CBD</th>
              <th class="py-1 pr-3 font-medium">THC</th>
              @for (p of plotted(); track p.symptom.id) {
                <th class="py-1 pr-3 font-medium">{{ p.symptom.name }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (day of daysWithData(); track day.day) {
              <tr class="border-b border-line/60">
                <td class="py-1 pr-3">{{ dayLabel(day.day) }}</td>
                <td class="py-1 pr-3">{{ day.doses }}</td>
                <td class="py-1 pr-3">{{ mgText(day.cbdMg) }}</td>
                <td class="py-1 pr-3">{{ mgText(day.thcMg) }}</td>
                @for (p of plotted(); track p.symptom.id) {
                  <td class="py-1 pr-3">{{ symptomValue(day, p.symptom.id) }}</td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    </details>
  `,
  imports: [NgTemplateOutlet],
})
export class TreatmentChart {
  readonly series = input.required<Series>();

  protected readonly left = MARGIN.left;
  protected readonly right = MARGIN.right;
  protected readonly top = MARGIN.top;
  protected readonly bottom = MARGIN.bottom;
  protected readonly mgHeight = MG_HEIGHT;
  protected readonly scaleHeight = SCALE_HEIGHT;
  protected readonly countHeight = COUNT_HEIGHT;
  protected readonly bg = SYMPTOM_BG;
  protected readonly hatchId = `thc-hatch-${nextId++}`;

  protected readonly width = signal(0);
  protected readonly hover = signal<number | null>(null);

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const measure = () => this.width.set(Math.floor(host.clientWidth));
      measure();
      if (typeof ResizeObserver !== 'undefined') {
        const observer = new ResizeObserver(measure);
        observer.observe(host);
        destroyRef.onDestroy(() => observer.disconnect());
      }
    });
  }

  /** Até 3 sintomas com registro no período; a posição define a cor e o tracejado. */
  protected readonly plotted = computed<Plotted[]>(() => {
    const days = this.series().days;
    return this.series()
      .symptoms.filter((symptom) => days.some((day) => day.symptoms[symptom.id] != null))
      .slice(0, MAX_SYMPTOMS)
      .map((symptom, index) => ({ symptom, index }));
  });
  protected readonly scalePlotted = computed(() => this.plotted().filter((p) => p.symptom.kind === 'SCALE'));
  protected readonly daysWithData = computed(() =>
    this.series().days.filter((day) => day.doses > 0 || Object.keys(day.symptoms).length > 0),
  );

  private readonly slot = computed(() => (this.width() - MARGIN.left - MARGIN.right) / Math.max(this.series().days.length, 1));
  private center(i: number): number {
    return MARGIN.left + this.slot() * i + this.slot() / 2;
  }
  private readonly barWidth = computed(() => Math.max(Math.min(this.slot() * 0.7, 18), 2));

  private readonly mgMax = computed(() =>
    niceCeiling(Math.max(0, ...this.series().days.map((d) => (d.cbdMg ?? 0) + (d.thcMg ?? 0)))),
  );

  protected readonly mgTicks = computed(() => ticks(this.mgMax(), MG_HEIGHT, (v) => formatCompact(v, 1)));
  protected readonly scaleTicks = computed(() => ticks(10, SCALE_HEIGHT, (v) => formatCompact(v, 1)));

  /** Barras empilhadas com 2 px de respiro entre CBD e THC e só a ponta de cima arredondada. */
  protected readonly mgBars = computed(() => {
    const plot = MG_HEIGHT - MARGIN.top - MARGIN.bottom;
    const base = MG_HEIGHT - MARGIN.bottom;
    const max = this.mgMax();
    const width = this.barWidth();
    return this.series().days.map((day, i) => {
      const x = this.center(i) - width / 2;
      const cbd = ((day.cbdMg ?? 0) / max) * plot;
      const thc = ((day.thcMg ?? 0) / max) * plot;
      const gap = cbd > 0 && thc > 0 ? 2 : 0;
      return {
        day: day.day,
        cbd: cbd > 0 ? barPath(x, base, width, cbd, thc === 0) : null,
        thc: thc > gap ? barPath(x, base - cbd - gap, width, thc - gap, true) : null,
      };
    });
  });

  protected readonly scaleLines = computed(() => {
    const plot = SCALE_HEIGHT - MARGIN.top - MARGIN.bottom;
    const base = SCALE_HEIGHT - MARGIN.bottom;
    return this.scalePlotted().map(({ symptom, index }) => {
      const segments: string[] = [];
      const points: { x: number; d: string }[] = [];
      let current: string[] = [];
      this.series().days.forEach((day, i) => {
        const value = day.symptoms[symptom.id];
        if (value == null) {
          // Dia sem nota quebra a linha: não inventamos valor entre os registros.
          if (current.length > 1) segments.push(current.join(' '));
          current = [];
          return;
        }
        const x = this.center(i);
        const y = base - (value / 10) * plot;
        current.push(`${current.length ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`);
        points.push({ x, d: marker(SHAPE[index], x, y) });
      });
      if (current.length > 1) segments.push(current.join(' '));
      return { id: symptom.id, segments, points, dash: DASH[index], stroke: SYMPTOM_STROKE[index], fill: SYMPTOM_FILL[index] };
    });
  });

  protected readonly countCharts = computed(() => {
    const plot = COUNT_HEIGHT - MARGIN.top - MARGIN.bottom;
    const base = COUNT_HEIGHT - MARGIN.bottom;
    const width = this.barWidth();
    return this.plotted()
      .filter((p) => p.symptom.kind === 'COUNT')
      .map(({ symptom, index }) => {
        const max = niceCeiling(Math.max(1, ...this.series().days.map((d) => d.symptoms[symptom.id] ?? 0)));
        return {
          id: symptom.id,
          name: symptom.name,
          fill: SYMPTOM_FILL[index],
          ticks: ticks(max, COUNT_HEIGHT, (v) => formatCompact(v, 0)).filter((t, i, all) => all.findIndex((o) => o.label === t.label) === i),
          bars: this.series()
            .days.map((day, i) => ({ day: day.day, value: day.symptoms[symptom.id] ?? 0, i }))
            .filter((b) => b.value > 0)
            .map((b) => ({ day: b.day, d: barPath(this.center(b.i) - width / 2, base, width, (b.value / max) * plot, true) })),
        };
      });
  });

  protected readonly xLabels = computed(() => {
    const days = this.series().days;
    const every = Math.max(1, Math.ceil(days.length / Math.max(2, Math.floor(this.width() / 56))));
    return days
      .map((day, i) => ({ x: this.center(i), text: shortDay(day.day), i }))
      .filter((label) => label.i % every === 0);
  });

  protected readonly hovered = computed(() => {
    const i = this.hover();
    return i == null ? null : { i, day: this.series().days[i] };
  });
  protected readonly hoverX = computed(() => {
    const i = this.hover();
    return i == null ? null : this.center(i);
  });
  protected readonly tooltipLeft = computed(() => {
    const x = this.hoverX() ?? 0;
    const tooltip = 192;
    return x + tooltip + 12 > this.width() ? Math.max(0, x - tooltip - 12) : x + 12;
  });

  protected onKey(event: KeyboardEvent): void {
    const days = this.series().days.length;
    if (!days) return;
    const current = this.hover() ?? days - 1;
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      this.hover.set(Math.min(current + 1, days - 1));
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault();
      this.hover.set(Math.max(this.hover() == null ? days - 1 : current - 1, 0));
    } else if (event.key === 'Escape') {
      this.hover.set(null);
    }
  }

  protected dayLabel(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('pt-BR', { weekday: 'short', day: '2-digit', month: 'short' }).format(new Date(y, m - 1, d));
  }

  protected mgText(value?: number): string {
    return value == null ? '—' : `${formatCompact(value, 1)} mg`;
  }

  protected symptomValue(day: DayPoint, symptomId: string): string {
    const value = day.symptoms[symptomId];
    return value == null ? 'sem nota' : formatCompact(value, 1);
  }
}

/** Retângulo com a ponta de cima arredondada (4 px) e a base reta, encostada no eixo. */
export function barPath(x: number, base: number, width: number, height: number, roundTop: boolean): string {
  const r = roundTop ? Math.min(4, width / 2, height) : 0;
  const top = base - height;
  return [
    `M${x},${base}`,
    `L${x},${top + r}`,
    r ? `Q${x},${top} ${x + r},${top}` : '',
    `L${x + width - r},${top}`,
    r ? `Q${x + width},${top} ${x + width},${top + r}` : '',
    `L${x + width},${base}`,
    'Z',
  ].join(' ');
}

function marker(shape: (typeof SHAPE)[number], x: number, y: number): string {
  const r = 4;
  if (shape === 'square') {
    return `M${x - r},${y - r} h${2 * r} v${2 * r} h${-2 * r} Z`;
  }
  if (shape === 'triangle') {
    return `M${x},${y - r - 1} L${x + r + 1},${y + r} L${x - r - 1},${y + r} Z`;
  }
  return `M${x - r},${y} a${r},${r} 0 1,0 ${2 * r},0 a${r},${r} 0 1,0 ${-2 * r},0`;
}

function ticks(max: number, height: number, label: (v: number) => string): { value: number; y: number; label: string }[] {
  const plot = height - MARGIN.top - MARGIN.bottom;
  return [0, 1, 2, 3, 4].map((i) => {
    const value = (max * i) / 4;
    return { value, y: height - MARGIN.bottom - (plot * i) / 4, label: label(value) };
  });
}

/** Topo do eixo em 1, 2 ou 5 × 10ⁿ. */
export function niceCeiling(max: number): number {
  if (!(max > 0)) return 10;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  for (const step of [1, 2, 5, 10]) {
    if (step * magnitude >= max) return step * magnitude;
  }
  return 10 * magnitude;
}

function shortDay(iso: string): string {
  const [, m, d] = iso.split('-');
  return `${d}/${m}`;
}
