import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { problemMessage } from '../core/api/problem';
import {
  DoseEntry,
  DoseUnit,
  Intensity,
  Medicine,
  Route,
  SideEffect,
  Symptom,
  TreatmentApi,
} from '../core/api/treatment.api';
import { parseDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { ScaleInput } from './scale-input';
import {
  DOSE_UNIT_LABEL,
  FORM_UNITS,
  INTENSITY_LABEL,
  ROUTE_LABEL,
  SIDE_EFFECT_LABEL,
  USUAL_ROUTE,
  concentration,
  mg,
  previewMg,
  quantity,
} from './treatment-text';

const EFFECTS = Object.keys(SIDE_EFFECT_LABEL) as SideEffect[];
const ROUTES = Object.keys(ROUTE_LABEL) as Route[];

/**
 * Registro rápido de um uso que já aconteceu: escolher o remédio, informar quanto e pronto. Hora, via, nota de
 * antes e efeitos são opcionais. O atalho "como da última vez" repete o último registro do mesmo remédio; não é
 * sugestão de quantidade.
 */
@Component({
  selector: 'app-dose-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, ScaleInput],
  template: `
    <form class="wd-card grid gap-4" (submit)="$event.preventDefault(); save()" aria-labelledby="dose-title">
      <h2 id="dose-title" class="text-lg font-semibold">Registrar uso</h2>

      <fieldset>
        <legend class="wd-label">Remédio</legend>
        <div class="grid gap-2 sm:grid-cols-2">
          @for (medicine of medicines(); track medicine.id) {
            <label class="cursor-pointer">
              <input type="radio" name="dose-medicine" class="peer sr-only" [checked]="selected()?.id === medicine.id" (change)="choose(medicine)" />
              <span class="block rounded-xl border border-line p-3 peer-checked:border-accent peer-checked:bg-accent/10 peer-focus-visible:outline-2 peer-focus-visible:outline-accent">
                <span class="block font-semibold">{{ medicine.name }}</span>
                <span class="block text-xs text-fg-muted">{{ concentrationOf(medicine) }}</span>
                <span class="block text-xs" [class.text-danger]="medicine.remaining <= 0">
                  Restam {{ remaining(medicine) }}{{ medicine.expired ? ' · vencido' : '' }}
                </span>
              </span>
            </label>
          } @empty {
            <p class="text-sm text-fg-muted">Cadastre um remédio na aba Remédios para registrar usos.</p>
          }
        </div>
      </fieldset>

      @if (selected(); as medicine) {
        @if (medicine.expired) {
          <p role="status" class="rounded-xl bg-warn/15 p-2 text-sm">Este lote está com a validade vencida. O registro é aceito, porque o histórico precisa mostrar o que foi usado.</p>
        }
        <div class="grid gap-3 sm:grid-cols-[1fr_auto]">
          <div>
            <label class="wd-label" for="dose-amount">Quanto foi usado</label>
            <div class="flex gap-2">
              <input id="dose-amount" class="wd-input w-32 text-base" inputmode="decimal" autocomplete="off" [value]="amountText()"
                     (input)="amountText.set($any($event.target).value)" required />
              <select class="wd-input w-auto text-base" aria-label="Unidade" [value]="unit()" (change)="unit.set($any($event.target).value)">
                @for (option of unitsOf(medicine); track option) {
                  <option [value]="option">{{ unitLabel[option] }}</option>
                }
              </select>
            </div>
            @if (lastUse(); as last) {
              <button type="button" class="mt-1 text-sm text-accent" (click)="repeat(last)">
                Como da última vez: {{ last.amount }} {{ unitLabel[last.unit] }}
              </button>
            }
          </div>
          <div>
            <label class="wd-label" for="dose-time">Quando</label>
            <input id="dose-time" type="datetime-local" class="wd-input text-base" [value]="takenAt()" [max]="nowLocal()"
                   (input)="takenAt.set($any($event.target).value)" />
          </div>
        </div>

        <p class="text-sm text-fg-muted" aria-live="polite">
          @if (preview(); as p) {
            Pelo rótulo: <strong class="text-fg">{{ mgText(p.cbd) }} de CBD</strong> e <strong class="text-fg">{{ mgText(p.thc) }} de THC</strong>.
          } @else if (unit() === 'APPLICATION') {
            Aplicação sem peso: fica no histórico, mas não desconta estoque nem calcula mg.
          }
        </p>

        <details class="rounded-xl border border-line p-3">
          <summary class="cursor-pointer font-medium">Como está antes? (opcional)</summary>
          <div class="mt-3 grid gap-4">
            @for (symptom of scaleSymptoms(); track symptom.id) {
              <app-scale-input [label]="symptom.name" [value]="before()[symptom.id] ?? null" (valueChange)="rate(symptom.id, $event)" />
            } @empty {
              <p class="text-sm text-fg-muted">Cadastre os sintomas que você acompanha na aba Ajustes.</p>
            }
          </div>
        </details>

        <details class="rounded-xl border border-line p-3">
          <summary class="cursor-pointer font-medium">Via, efeitos e observação (opcional)</summary>
          <div class="mt-3 grid gap-3">
            <div>
              <label class="wd-label" for="dose-route">Via</label>
              <select id="dose-route" class="wd-input text-base" [value]="route()" (change)="route.set($any($event.target).value)">
                @for (option of routes; track option) {
                  <option [value]="option">{{ routeLabel[option] }}</option>
                }
              </select>
            </div>
            <fieldset>
              <legend class="wd-label">Efeitos que você percebeu</legend>
              <div class="flex flex-wrap gap-2">
                @for (effect of effects; track effect) {
                  <button type="button" class="min-h-10 rounded-full border px-3 text-sm"
                          [class]="effectsChosen()[effect] ? 'border-accent bg-accent/15 font-semibold' : 'border-line'"
                          [attr.aria-pressed]="!!effectsChosen()[effect]" (click)="toggleEffect(effect)">
                    {{ effectLabel[effect] }}{{ effectsChosen()[effect] ? ' · ' + intensityLabel[effectsChosen()[effect]!] : '' }}
                  </button>
                }
              </div>
              <p class="mt-1 text-xs text-fg-muted">Toque de novo para mudar a intensidade (leve → moderado → forte → remover).</p>
            </fieldset>
            <div>
              <label class="wd-label" for="dose-notes">Observação</label>
              <textarea id="dose-notes" rows="2" maxlength="500" class="wd-input py-2 text-base" [value]="notes()"
                        (input)="notes.set($any($event.target).value)"></textarea>
            </div>
          </div>
        </details>
      }

      @if (error()) {
        <p role="alert" class="text-sm text-danger">{{ error() }}</p>
      }
      <div class="flex gap-2">
        <button type="submit" class="wd-button" [disabled]="!canSave() || busy()">
          <app-icon name="check" class="size-5" /> Registrar
        </button>
        <button type="button" class="wd-button bg-surface-raised text-fg" (click)="cancelled.emit()">Cancelar</button>
      </div>
    </form>
  `,
})
export class DoseForm {
  private readonly api = inject(TreatmentApi);

  readonly patientId = input.required<string>();
  readonly medicines = input.required<Medicine[]>();
  readonly symptoms = input.required<Symptom[]>();
  /** Registros recentes, para o atalho "como da última vez". */
  readonly recent = input<DoseEntry[]>([]);
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly unitLabel = DOSE_UNIT_LABEL;
  protected readonly routeLabel = ROUTE_LABEL;
  protected readonly effectLabel = SIDE_EFFECT_LABEL;
  protected readonly intensityLabel = INTENSITY_LABEL;
  protected readonly effects = EFFECTS;
  protected readonly routes = ROUTES;

  protected readonly selected = signal<Medicine | null>(null);
  protected readonly amountText = signal('');
  protected readonly unit = signal<DoseUnit>('DROP');
  protected readonly route = signal<Route>('SUBLINGUAL');
  protected readonly takenAt = signal(localNow());
  protected readonly notes = signal('');
  protected readonly before = signal<Record<string, number>>({});
  protected readonly effectsChosen = signal<Partial<Record<SideEffect, Intensity>>>({});
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly scaleSymptoms = computed(() => this.symptoms().filter((s) => s.kind === 'SCALE'));
  protected readonly amount = computed(() => parseDecimal(this.amountText()));
  protected readonly canSave = computed(() => !!this.selected() && (this.amount() ?? 0) > 0);
  protected readonly preview = computed(() => {
    const medicine = this.selected();
    const amount = this.amount();
    if (!medicine || !amount || this.unit() === 'APPLICATION') {
      return null;
    }
    const p = previewMg(medicine, amount, this.unit());
    return p.thc == null && p.cbd == null ? null : p;
  });
  protected readonly lastUse = computed(() => {
    const medicine = this.selected();
    return medicine ? (this.recent().find((dose) => dose.batchId === medicine.id) ?? null) : null;
  });

  constructor() {
    // Com um remédio só, já vem escolhido.
    effect(() => {
      const list = this.medicines();
      if (list.length === 1 && !this.selected()) {
        this.choose(list[0]);
      }
    });
  }

  protected choose(medicine: Medicine): void {
    this.selected.set(medicine);
    this.unit.set(FORM_UNITS[medicine.form].dose[0]);
    this.route.set(USUAL_ROUTE[medicine.form]);
  }

  protected repeat(last: DoseEntry): void {
    this.amountText.set(String(last.amount).replace('.', ','));
    this.unit.set(last.unit);
    this.route.set(last.route);
  }

  protected rate(symptomId: string, value: number | null): void {
    this.before.update((current) => {
      const next = { ...current };
      if (value == null) {
        delete next[symptomId];
      } else {
        next[symptomId] = value;
      }
      return next;
    });
  }

  protected toggleEffect(effect: SideEffect): void {
    const order: (Intensity | undefined)[] = ['MILD', 'MODERATE', 'SEVERE', undefined];
    this.effectsChosen.update((current) => {
      const next = { ...current };
      const following = order[(order.indexOf(current[effect]) + 1) % order.length];
      if (following) {
        next[effect] = following;
      } else {
        delete next[effect];
      }
      return next;
    });
  }

  protected save(): void {
    const medicine = this.selected();
    const amount = this.amount();
    if (!medicine || !amount) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .recordDose(this.patientId(), {
        batchId: medicine.id,
        amount,
        unit: this.unit(),
        route: this.route(),
        takenAt: new Date(this.takenAt()).toISOString(),
        notes: this.notes().trim() || undefined,
        before: Object.entries(this.before()).map(([symptomId, value]) => ({ symptomId, value })),
        sideEffects: Object.entries(this.effectsChosen()).map(([effect, intensity]) => ({
          effect: effect as SideEffect,
          intensity: intensity as Intensity,
        })),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.saved.emit();
        },
        error: (error) => {
          this.busy.set(false);
          this.error.set(problemMessage(error));
        },
      });
  }

  protected unitsOf(medicine: Medicine): DoseUnit[] {
    return FORM_UNITS[medicine.form].dose;
  }

  protected concentrationOf(medicine: Medicine): string {
    return concentration(medicine);
  }

  protected remaining(medicine: Medicine): string {
    return quantity(medicine.remaining, medicine.stockUnit);
  }

  protected mgText(value?: number): string {
    return mg(value);
  }

  protected nowLocal(): string {
    return localNow();
  }
}

/** "yyyy-MM-ddTHH:mm" no fuso do aparelho, para o input datetime-local. */
function localNow(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}
