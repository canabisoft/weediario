import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { DoseEntry, Rating, Series, SideEffectEntry, Timeline, TreatmentApi } from '../core/api/treatment.api';
import { TreatmentChart } from './treatment-chart';
import { DOSE_UNIT_LABEL, INTENSITY_LABEL, SIDE_EFFECT_LABEL, clock, isoDay, mg } from './treatment-text';

const PERIODS = [7, 30, 90] as const;

interface DayGroup {
  readonly day: string;
  readonly doses: DoseEntry[];
  readonly checkins: Rating[];
  readonly sideEffects: SideEffectEntry[];
}

/** Evolução do tratamento: gráficos do período e, embaixo, tudo o que foi registrado dia a dia. */
@Component({
  selector: 'app-history-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TreatmentChart],
  template: `
    <div class="flex flex-wrap gap-2" role="radiogroup" aria-label="Período">
      @for (option of periods; track option) {
        <button type="button" role="radio" class="min-h-10 rounded-full px-4 text-sm font-semibold"
                [class]="days() === option ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
                [attr.aria-checked]="days() === option" (click)="days.set(option)">
          {{ option }} dias
        </button>
      }
    </div>

    @if (data(); as d) {
      <section class="wd-card mt-4" aria-label="Gráficos do período">
        <app-treatment-chart [series]="d.series" />
      </section>

      <h2 class="mt-6 text-lg font-semibold">Registros</h2>
      <ol class="mt-3 grid gap-4">
        @for (group of groups(); track group.day) {
          <li>
            <h3 class="text-sm font-semibold text-fg-muted">{{ dayLabel(group.day) }}</h3>
            <ul class="mt-2 grid gap-2">
              @for (dose of group.doses; track dose.id) {
                <li class="wd-card grid gap-1 p-3">
                  <p class="flex flex-wrap items-baseline gap-x-2">
                    <strong class="tabular-nums">{{ time(dose.takenAt) }}</strong>
                    <span>{{ dose.batchName }}</span>
                    <span class="text-fg-muted">{{ amount(dose) }}</span>
                    <span class="ml-auto text-xs text-fg-muted">CBD {{ mgText(dose.cbdMg) }} · THC {{ mgText(dose.thcMg) }}</span>
                  </p>
                  @for (line of ratingLines(dose); track line) {
                    <p class="text-sm">{{ line }}</p>
                  }
                  @if (dose.sideEffects.length) {
                    <p class="text-sm text-fg-muted">Efeitos: {{ effects(dose.sideEffects) }}</p>
                  }
                  @if (dose.notes) {
                    <p class="text-sm text-fg-muted">{{ dose.notes }}</p>
                  }
                  @if (dose.recordedBy) {
                    <p class="text-xs text-fg-muted">Registrado por {{ dose.recordedBy }}</p>
                  }
                </li>
              }
              @for (rating of group.checkins; track rating.id) {
                <li class="wd-card p-3 text-sm">
                  <strong class="tabular-nums">{{ time(rating.recordedAt) }}</strong> · Check-in: {{ rating.symptom }}
                  {{ rating.kind === 'COUNT' ? rating.value + ' vez(es)' : rating.value + ' de 10' }}
                </li>
              }
              @if (group.sideEffects.length) {
                <li class="wd-card p-3 text-sm text-fg-muted">Efeitos anotados sem uso ligado: {{ effects(group.sideEffects) }}</li>
              }
            </ul>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nada registrado neste período.</li>
        }
      </ol>
    } @else {
      <div class="mt-4 h-64 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class HistoryPanel {
  private readonly api = inject(TreatmentApi);

  readonly patientId = input.required<string>();

  protected readonly periods = PERIODS;
  protected readonly days = signal<number>(30);

  protected readonly data = toSignal(
    combineLatest([toObservable(this.patientId), toObservable(this.days)]).pipe(
      switchMap(([id, days]) => {
        const to = new Date();
        const from = new Date(to.getFullYear(), to.getMonth(), to.getDate() - (days - 1));
        return combineLatest([this.api.series(id, isoDay(from), isoDay(to)), this.api.timeline(id, isoDay(from), isoDay(to))]).pipe(
          map(([series, timeline]) => ({ series, timeline }) as { series: Series; timeline: Timeline } | null),
          catchError(() => of(null)),
        );
      }),
    ),
  );

  protected readonly groups = computed<DayGroup[]>(() => {
    const timeline = this.data()?.timeline;
    if (!timeline) return [];
    const byDay = new Map<string, { doses: DoseEntry[]; checkins: Rating[]; sideEffects: SideEffectEntry[] }>();
    const at = (day: string) => {
      let group = byDay.get(day);
      if (!group) {
        group = { doses: [], checkins: [], sideEffects: [] };
        byDay.set(day, group);
      }
      return group;
    };
    timeline.doses.forEach((dose) => at(isoDay(new Date(dose.takenAt))).doses.push(dose));
    timeline.checkins.forEach((rating) => at(isoDay(new Date(rating.recordedAt))).checkins.push(rating));
    timeline.sideEffects.forEach((effect) => at(isoDay(new Date(effect.recordedAt))).sideEffects.push(effect));
    return [...byDay.entries()].sort(([a], [b]) => b.localeCompare(a)).map(([day, group]) => ({ day, ...group }));
  });

  protected dayLabel(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }).format(new Date(y, m - 1, d));
  }

  protected time(instant: string): string {
    return clock(instant);
  }

  protected mgText(value?: number): string {
    return mg(value);
  }

  protected amount(dose: DoseEntry): string {
    return `${dose.amount.toLocaleString('pt-BR')} ${DOSE_UNIT_LABEL[dose.unit]}`;
  }

  protected ratingLines(dose: DoseEntry): string[] {
    const bySymptom = new Map<string, { name: string; before?: number; after?: number }>();
    for (const rating of dose.ratings) {
      const entry = bySymptom.get(rating.symptomId) ?? { name: rating.symptom };
      if (rating.moment === 'BEFORE') entry.before = rating.value;
      if (rating.moment === 'AFTER') entry.after = rating.value;
      bySymptom.set(rating.symptomId, entry);
    }
    return [...bySymptom.values()].map((e) => `${e.name}: ${e.before ?? '—'} antes, ${e.after ?? '—'} depois`);
  }

  protected effects(list: SideEffectEntry[]): string {
    return list.map((e) => `${SIDE_EFFECT_LABEL[e.effect].toLowerCase()} (${INTENSITY_LABEL[e.intensity].toLowerCase()})`).join(', ');
  }
}
