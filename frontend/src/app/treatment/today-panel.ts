import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { DoseEntry, PendingAfter, Today, TreatmentApi } from '../core/api/treatment.api';
import { Icon } from '../shared/icon';
import { DoseForm } from './dose-form';
import { ScaleInput } from './scale-input';
import { DOSE_UNIT_LABEL, SIDE_EFFECT_LABEL, clock, mg, quantity } from './treatment-text';

/**
 * O dia do paciente: registrar uso em poucos toques, responder "como está agora?" depois de um uso, check-in
 * sem uso (ex.: quantas crises hoje) e o estoque de cada remédio.
 */
@Component({
  selector: 'app-today-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, DoseForm, ScaleInput],
  template: `
    @let t = today();
    @if (adding()) {
      <app-dose-form [patientId]="patientId()" [medicines]="t.medicines" [symptoms]="t.symptoms" [recent]="t.doses"
                     (saved)="adding.set(false); changed.emit()" (cancelled)="adding.set(false)" />
    } @else {
      <button type="button" class="wd-button w-full py-4 text-base sm:w-auto" (click)="adding.set(true)">
        <app-icon name="plus" class="size-5" [strokeWidth]="2.4" /> Registrar uso
      </button>
    }

    @for (pending of t.pendingAfter; track pending.doseId) {
      <section class="wd-card mt-4 grid gap-3 border-accent/60" [attr.aria-label]="'Como está depois do uso das ' + time(pending.takenAt)">
        <p class="font-semibold">Como está agora? <span class="font-normal text-fg-muted">Uso das {{ time(pending.takenAt) }} · {{ pending.batchName }}</span></p>
        @for (symptom of pending.symptoms; track symptom.id) {
          <app-scale-input [label]="symptom.name" [value]="afterValue(pending, symptom.id)" (valueChange)="setAfter(pending, symptom.id, $event)" />
        }
        <button type="button" class="wd-button w-fit" [disabled]="busy() || !hasAfter(pending)" (click)="saveAfter(pending)">Salvar notas</button>
      </section>
    }

    <h2 class="mt-6 text-lg font-semibold">Hoje</h2>
    <p class="text-sm text-fg-muted">
      Total registrado: <strong class="text-fg">{{ mgText(t.cbdMgToday) }}</strong> de CBD ·
      <strong class="text-fg">{{ mgText(t.thcMgToday) }}</strong> de THC
    </p>
    <ul class="mt-3 grid gap-2">
      @for (dose of t.doses; track dose.id) {
        <li class="wd-card grid gap-1">
          <p class="flex flex-wrap items-baseline gap-x-2">
            <strong class="tabular-nums">{{ time(dose.takenAt) }}</strong>
            <span>{{ dose.batchName }}</span>
            <span class="text-fg-muted">{{ amount(dose) }}</span>
            <span class="ml-auto text-xs text-fg-muted">CBD {{ mgText(dose.cbdMg) }} · THC {{ mgText(dose.thcMg) }}</span>
          </p>
          @if (ratingsText(dose); as ratings) {
            <p class="text-sm">{{ ratings }}</p>
          }
          @if (dose.sideEffects.length) {
            <p class="text-sm text-fg-muted">Efeitos: {{ effectsText(dose) }}</p>
          }
          @if (dose.notes) {
            <p class="text-sm text-fg-muted">{{ dose.notes }}</p>
          }
          <p class="flex items-center gap-2 text-xs text-fg-muted">
            @if (dose.recordedBy) {
              <span>Registrado por {{ dose.recordedBy }}</span>
            }
            @if (confirmDelete() === dose.id) {
              <button type="button" class="ml-auto text-danger" (click)="remove(dose)">Confirmar exclusão</button>
              <button type="button" (click)="confirmDelete.set(null)">Cancelar</button>
            } @else {
              <button type="button" class="ml-auto" (click)="confirmDelete.set(dose.id)">Apagar (registro errado)</button>
            }
          </p>
        </li>
      } @empty {
        <li class="wd-card text-center text-fg-muted">Nenhum uso registrado hoje.</li>
      }
    </ul>

    @if (t.symptoms.length) {
      <details class="wd-card mt-4">
        <summary class="cursor-pointer font-semibold">Check-in sem uso</summary>
        <p class="mt-1 text-sm text-fg-muted">Para anotar como está agora, ou eventos do dia, mesmo sem ter usado nada.</p>
        <div class="mt-3 grid gap-4">
          @for (symptom of t.symptoms; track symptom.id) {
            <app-scale-input [label]="symptom.name" [kind]="symptom.kind" [value]="checkin()[symptom.id] ?? null"
                             (valueChange)="setCheckin(symptom.id, $event)" />
          }
          <button type="button" class="wd-button w-fit" [disabled]="busy() || !checkinCount()" (click)="saveCheckin()">Salvar check-in</button>
        </div>
      </details>
    }

    <h2 class="mt-6 text-lg font-semibold">Estoque</h2>
    <ul class="mt-3 grid gap-2 sm:grid-cols-2">
      @for (medicine of t.medicines; track medicine.id) {
        <li class="wd-card">
          <p class="font-semibold">{{ medicine.name }}</p>
          <p class="text-sm">Restam {{ remaining(medicine.remaining, medicine.stockUnit) }} de {{ remaining(medicine.initialQuantity, medicine.stockUnit) }}</p>
          @if (medicine.estimatedDaysLeft != null) {
            <p class="text-xs text-fg-muted">No ritmo das últimas 2 semanas, dura cerca de {{ medicine.estimatedDaysLeft }} dias.</p>
          }
          @if (medicine.expired) {
            <p class="mt-1 text-xs font-semibold text-danger">Validade vencida</p>
          } @else if (medicine.expiresSoon) {
            <p class="mt-1 text-xs font-semibold text-warn">Vence em breve</p>
          }
        </li>
      }
    </ul>

    @if (error()) {
      <p role="alert" class="mt-3 text-sm text-danger">{{ error() }}</p>
    }
  `,
})
export class TodayPanel {
  private readonly api = inject(TreatmentApi);

  readonly patientId = input.required<string>();
  readonly today = input.required<Today>();
  readonly changed = output<void>();

  protected readonly adding = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmDelete = signal<string | null>(null);
  private readonly after = signal<Record<string, Record<string, number>>>({});
  protected readonly checkin = signal<Record<string, number>>({});
  protected readonly checkinCount = computed(() => Object.keys(this.checkin()).length);

  protected time(instant: string): string {
    return clock(instant);
  }

  protected mgText(value?: number): string {
    return mg(value);
  }

  protected amount(dose: DoseEntry): string {
    return `${dose.amount.toLocaleString('pt-BR')} ${DOSE_UNIT_LABEL[dose.unit]}`;
  }

  protected remaining(value: number, unit: 'ML' | 'G' | 'UNIT'): string {
    return quantity(value, unit);
  }

  /** "Dor: 7 antes, 3 depois". */
  protected ratingsText(dose: DoseEntry): string {
    const bySymptom = new Map<string, { name: string; before?: number; after?: number }>();
    for (const rating of dose.ratings) {
      const entry = bySymptom.get(rating.symptomId) ?? { name: rating.symptom };
      if (rating.moment === 'BEFORE') entry.before = rating.value;
      if (rating.moment === 'AFTER') entry.after = rating.value;
      bySymptom.set(rating.symptomId, entry);
    }
    return [...bySymptom.values()]
      .map((e) => `${e.name}: ${e.before ?? '—'} antes, ${e.after ?? '—'} depois`)
      .join(' · ');
  }

  protected effectsText(dose: DoseEntry): string {
    return dose.sideEffects.map((e) => SIDE_EFFECT_LABEL[e.effect].toLowerCase()).join(', ');
  }

  protected afterValue(pending: PendingAfter, symptomId: string): number | null {
    return this.after()[pending.doseId]?.[symptomId] ?? null;
  }

  protected hasAfter(pending: PendingAfter): boolean {
    return Object.keys(this.after()[pending.doseId] ?? {}).length > 0;
  }

  protected setAfter(pending: PendingAfter, symptomId: string, value: number | null): void {
    this.after.update((all) => {
      const forDose = { ...(all[pending.doseId] ?? {}) };
      if (value == null) delete forDose[symptomId];
      else forDose[symptomId] = value;
      return { ...all, [pending.doseId]: forDose };
    });
  }

  protected saveAfter(pending: PendingAfter): void {
    const ratings = Object.entries(this.after()[pending.doseId] ?? {}).map(([symptomId, value]) => ({ symptomId, value }));
    this.run(this.api.rateAfter(this.patientId(), pending.doseId, ratings));
  }

  protected setCheckin(symptomId: string, value: number | null): void {
    this.checkin.update((current) => {
      const next = { ...current };
      if (value == null) delete next[symptomId];
      else next[symptomId] = value;
      return next;
    });
  }

  protected saveCheckin(): void {
    const ratings = Object.entries(this.checkin()).map(([symptomId, value]) => ({ symptomId, value }));
    this.run(this.api.checkin(this.patientId(), ratings), () => this.checkin.set({}));
  }

  protected remove(dose: DoseEntry): void {
    this.run(this.api.deleteDose(this.patientId(), dose.id), () => this.confirmDelete.set(null));
  }

  private run(request: Observable<unknown>, done?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        done?.();
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
