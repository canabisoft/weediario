import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { Medicine, MedicineForm, Origin, TreatmentApi } from '../core/api/treatment.api';
import { formatDate } from '../shared/labels';
import { parseDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import {
  CONCENTRATION_LABEL,
  FORM_LABEL,
  FORM_UNITS,
  ORIGIN_LABEL,
  STOCK_UNIT_LABEL,
  concentration,
  quantity,
} from './treatment-text';

const FORMS = Object.keys(FORM_LABEL) as MedicineForm[];
const ORIGINS = Object.keys(ORIGIN_LABEL) as Origin[];

/**
 * Remédios prontos do paciente (lotes), com concentração do rótulo, validade e saldo. Correções de estoque
 * viram ajustes, para o histórico nunca ser reescrito.
 */
@Component({
  selector: 'app-medicines-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <button type="button" class="wd-button" (click)="adding.set(!adding())">
        <app-icon name="plus" class="size-5" /> Novo remédio
      </button>
      <label class="ml-auto flex items-center gap-2 text-sm">
        <input type="checkbox" class="size-5" [checked]="showArchived()" (change)="showArchived.set($any($event.target).checked)" />
        Mostrar arquivados
      </label>
    </div>

    @if (adding()) {
      <form class="wd-card mt-4 grid gap-3 sm:grid-cols-2" (submit)="$event.preventDefault(); register()" aria-label="Novo remédio">
        <div class="sm:col-span-2">
          <label class="wd-label" for="med-name">Nome (como está no frasco)</label>
          <input id="med-name" class="wd-input" maxlength="80" [value]="name()" (input)="name.set($any($event.target).value)"
                 placeholder="Ex.: Óleo CBD 20:1 da associação" />
        </div>
        <div>
          <label class="wd-label" for="med-form">Forma</label>
          <select id="med-form" class="wd-input text-base" [value]="form()" (change)="form.set($any($event.target).value)">
            @for (option of forms; track option) {
              <option [value]="option">{{ formLabel[option] }}</option>
            }
          </select>
        </div>
        <div>
          <label class="wd-label" for="med-origin">Origem</label>
          <select id="med-origin" class="wd-input text-base" [value]="origin()" (change)="origin.set($any($event.target).value)">
            @for (option of origins; track option) {
              <option [value]="option">{{ originLabel[option] }}</option>
            }
          </select>
        </div>
        <div>
          <label class="wd-label" for="med-cbd">CBD ({{ concentrationUnit() }})</label>
          <input id="med-cbd" class="wd-input" inputmode="decimal" [value]="cbd()" (input)="cbd.set($any($event.target).value)" placeholder="do rótulo ou laudo" />
        </div>
        <div>
          <label class="wd-label" for="med-thc">THC ({{ concentrationUnit() }})</label>
          <input id="med-thc" class="wd-input" inputmode="decimal" [value]="thc()" (input)="thc.set($any($event.target).value)" placeholder="do rótulo ou laudo" />
        </div>
        <div>
          <label class="wd-label" for="med-qty">Quantidade no frasco/pote ({{ stockUnit() }})</label>
          <input id="med-qty" class="wd-input" inputmode="decimal" [value]="initial()" (input)="initial.set($any($event.target).value)" required />
        </div>
        @if (usesDrops()) {
          <div>
            <label class="wd-label" for="med-drops">Gotas por ml do conta-gotas</label>
            <input id="med-drops" class="wd-input" inputmode="decimal" [value]="drops()" (input)="drops.set($any($event.target).value)" />
            <p class="mt-1 text-xs text-fg-muted">Padrão 20. Para conferir: conte as gotas de 1 ml numa seringa.</p>
          </div>
        }
        <div>
          <label class="wd-label" for="med-made">Fabricação</label>
          <input id="med-made" type="date" class="wd-input text-base" [value]="manufactured()" (input)="manufactured.set($any($event.target).value)" />
        </div>
        <div>
          <label class="wd-label" for="med-exp">Validade</label>
          <input id="med-exp" type="date" class="wd-input text-base" [value]="expires()" (input)="expires.set($any($event.target).value)" />
        </div>
        <div class="sm:col-span-2">
          <label class="wd-label" for="med-notes">Observações</label>
          <textarea id="med-notes" rows="2" maxlength="1000" class="wd-input py-2 text-base" [value]="notes()" (input)="notes.set($any($event.target).value)"></textarea>
        </div>
        <div class="flex gap-2 sm:col-span-2">
          <button type="submit" class="wd-button" [disabled]="!canRegister() || busy()">Salvar remédio</button>
          <button type="button" class="wd-button bg-surface-raised text-fg" (click)="adding.set(false)">Cancelar</button>
        </div>
      </form>
    }

    <ul class="mt-4 grid gap-3">
      @for (medicine of list() ?? []; track medicine.id) {
        <li class="wd-card grid gap-2" [class.opacity-70]="medicine.archived">
          <div class="flex flex-wrap items-start gap-2">
            <div class="min-w-0 flex-1">
              <p class="font-semibold">{{ medicine.name }}{{ medicine.archived ? ' (arquivado)' : '' }}</p>
              <p class="text-sm text-fg-muted">{{ formLabel[medicine.form] }} · {{ originLabel[medicine.origin] }} · {{ concentrationText(medicine) }}</p>
            </div>
            <p class="text-right">
              <strong class="block tabular-nums">{{ amountText(medicine.remaining, medicine) }}</strong>
              <span class="text-xs text-fg-muted">de {{ amountText(medicine.initialQuantity, medicine) }}</span>
            </p>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-surface-raised" role="img"
               [attr.aria-label]="'Restam ' + amountText(medicine.remaining, medicine) + ' de ' + amountText(medicine.initialQuantity, medicine)">
            <div class="h-full rounded-full bg-accent" [style.width.%]="percent(medicine)"></div>
          </div>
          <p class="flex flex-wrap gap-x-3 text-xs text-fg-muted">
            @if (medicine.expiresOn) {
              <span [class.text-danger]="medicine.expired" [class.font-semibold]="medicine.expired || medicine.expiresSoon">
                {{ medicine.expired ? 'Vencido em' : 'Validade' }} {{ date(medicine.expiresOn) }}
              </span>
            }
            @if (medicine.dropsPerMl) {
              <span>{{ medicine.dropsPerMl }} gotas/ml</span>
            }
            @if (medicine.estimatedDaysLeft != null) {
              <span>dura ~{{ medicine.estimatedDaysLeft }} dias no ritmo atual</span>
            }
          </p>
          @if (medicine.notes) {
            <p class="text-sm text-fg-muted">{{ medicine.notes }}</p>
          }

          @if (adjusting() === medicine.id) {
            <form class="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-[auto_1fr_auto]" (submit)="$event.preventDefault(); adjust(medicine)">
              <select class="wd-input text-base" aria-label="Tipo de ajuste" [value]="adjustKind()" (change)="adjustKind.set($any($event.target).value)">
                <option value="DISCARD">Descartar (perda, vencido)</option>
                <option value="ADJUSTMENT">Corrigir contagem (+ ou −)</option>
              </select>
              <input class="wd-input" inputmode="decimal" [attr.aria-label]="'Quantidade em ' + stockLabel[medicine.stockUnit]"
                     [placeholder]="'Quantidade (' + stockLabel[medicine.stockUnit] + ')'" [value]="adjustQty()" (input)="adjustQty.set($any($event.target).value)" />
              <button type="submit" class="wd-button" [disabled]="!adjustAmount() || busy()">Aplicar</button>
            </form>
          }
          <div class="flex flex-wrap gap-2">
            @if (!medicine.archived) {
              <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" (click)="startAdjust(medicine)">Ajustar estoque</button>
            }
            <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" (click)="archive(medicine)">
              {{ medicine.archived ? 'Reativar' : 'Arquivar' }}
            </button>
          </div>
        </li>
      } @empty {
        <li class="wd-card text-center text-fg-muted">Nenhum remédio cadastrado.</li>
      }
    </ul>

    @if (error()) {
      <p role="alert" class="mt-3 text-sm text-danger">{{ error() }}</p>
    }
  `,
})
export class MedicinesPanel {
  private readonly api = inject(TreatmentApi);

  readonly patientId = input.required<string>();
  readonly changed = output<void>();

  protected readonly forms = FORMS;
  protected readonly origins = ORIGINS;
  protected readonly formLabel = FORM_LABEL;
  protected readonly originLabel = ORIGIN_LABEL;
  protected readonly stockLabel = STOCK_UNIT_LABEL;

  protected readonly adding = signal(false);
  protected readonly showArchived = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly reload = signal(0);

  protected readonly name = signal('');
  protected readonly form = signal<MedicineForm>('OIL');
  protected readonly origin = signal<Origin>('ASSOCIATION');
  protected readonly thc = signal('');
  protected readonly cbd = signal('');
  protected readonly initial = signal('');
  protected readonly drops = signal('20');
  protected readonly manufactured = signal('');
  protected readonly expires = signal('');
  protected readonly notes = signal('');

  protected readonly adjusting = signal<string | null>(null);
  protected readonly adjustKind = signal<'DISCARD' | 'ADJUSTMENT'>('DISCARD');
  protected readonly adjustQty = signal('');
  protected readonly adjustAmount = computed(() => {
    const value = parseDecimal(this.adjustQty());
    return value != null && value !== 0 ? value : null;
  });

  protected readonly concentrationUnit = computed(() => CONCENTRATION_LABEL[FORM_UNITS[this.form()].concentration]);
  protected readonly stockUnit = computed(() => STOCK_UNIT_LABEL[FORM_UNITS[this.form()].stock]);
  protected readonly usesDrops = computed(() => FORM_UNITS[this.form()].dose.includes('DROP'));
  protected readonly canRegister = computed(() => !!this.name().trim() && (parseDecimal(this.initial()) ?? 0) > 0);

  protected readonly list = toSignal(
    combineLatest([toObservable(this.patientId), toObservable(this.showArchived), toObservable(this.reload)]).pipe(
      switchMap(([id, archived]) => this.api.medicines(id, archived).pipe(catchError(() => of<Medicine[]>([])))),
    ),
  );

  protected concentrationText(medicine: Medicine): string {
    return concentration(medicine);
  }

  protected amountText(value: number, medicine: Medicine): string {
    return quantity(value, medicine.stockUnit);
  }

  protected percent(medicine: Medicine): number {
    return Math.max(0, Math.min(100, (medicine.remaining / medicine.initialQuantity) * 100));
  }

  protected date(value: string): string {
    return formatDate(value);
  }

  protected register(): void {
    this.run(
      this.api.registerMedicine(this.patientId(), {
        name: this.name().trim(),
        form: this.form(),
        origin: this.origin(),
        thc: parseDecimal(this.thc()) ?? undefined,
        cbd: parseDecimal(this.cbd()) ?? undefined,
        dropsPerMl: this.usesDrops() ? (parseDecimal(this.drops()) ?? undefined) : undefined,
        initialQuantity: parseDecimal(this.initial()) ?? 0,
        manufacturedOn: this.manufactured() || undefined,
        expiresOn: this.expires() || undefined,
        notes: this.notes().trim() || undefined,
      }),
      () => {
        this.adding.set(false);
        for (const field of [this.name, this.thc, this.cbd, this.initial, this.manufactured, this.expires, this.notes]) {
          field.set('');
        }
      },
    );
  }

  protected startAdjust(medicine: Medicine): void {
    this.adjustQty.set('');
    this.adjusting.set(this.adjusting() === medicine.id ? null : medicine.id);
  }

  protected adjust(medicine: Medicine): void {
    const amount = this.adjustAmount();
    if (amount == null) {
      return;
    }
    this.run(this.api.adjustStock(this.patientId(), medicine.id, this.adjustKind(), amount), () => this.adjusting.set(null));
  }

  protected archive(medicine: Medicine): void {
    this.run(this.api.archiveMedicine(this.patientId(), medicine.id, !medicine.archived));
  }

  private run(request: Observable<unknown>, done?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        done?.();
        this.reload.update((n) => n + 1);
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
