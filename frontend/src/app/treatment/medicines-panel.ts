import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { Medicine, TreatmentApi } from '../core/api/treatment.api';
import { formatDate } from '../shared/labels';
import { parseDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { MedicineEditor } from './medicine-editor';
import { FORM_LABEL, ORIGIN_LABEL, STOCK_UNIT_LABEL, concentration, quantity } from './treatment-text';

/**
 * Remédios prontos do paciente (lotes), com concentração do rótulo, validade e saldo. Os dados do rótulo podem ser
 * editados; correções de estoque viram ajustes, para o histórico nunca ser reescrito.
 */
@Component({
  selector: 'app-medicines-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, MedicineEditor],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      <button type="button" class="wd-button" (click)="adding.set(!adding()); editing.set(null)">
        <app-icon name="plus" class="size-5" /> Novo remédio
      </button>
      <label class="ml-auto flex items-center gap-2 text-sm">
        <input type="checkbox" class="size-5" [checked]="showArchived()" (change)="showArchived.set($any($event.target).checked)" />
        Mostrar arquivados
      </label>
    </div>

    @if (adding()) {
      <app-medicine-editor class="mt-4 block" [patientId]="patientId()" (saved)="saved()" (cancelled)="adding.set(false)" />
    }

    <ul class="mt-4 grid gap-3">
      @for (medicine of list() ?? []; track medicine.id) {
        @if (editing() === medicine.id) {
          <li>
            <app-medicine-editor [patientId]="patientId()" [medicine]="medicine" (saved)="saved()" (cancelled)="editing.set(null)" />
          </li>
        } @else {
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
              <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" (click)="startEdit(medicine)">Editar</button>
              <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" (click)="startAdjust(medicine)">Ajustar estoque</button>
            }
            <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" (click)="archive(medicine)">
              {{ medicine.archived ? 'Reativar' : 'Arquivar' }}
            </button>
          </div>
        </li>
        }
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

  protected readonly formLabel = FORM_LABEL;
  protected readonly originLabel = ORIGIN_LABEL;
  protected readonly stockLabel = STOCK_UNIT_LABEL;

  protected readonly adding = signal(false);
  /** Remédio aberto para edição (um por vez). */
  protected readonly editing = signal<string | null>(null);
  protected readonly showArchived = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly reload = signal(0);

  protected readonly adjusting = signal<string | null>(null);
  protected readonly adjustKind = signal<'DISCARD' | 'ADJUSTMENT'>('DISCARD');
  protected readonly adjustQty = signal('');
  protected readonly adjustAmount = computed(() => {
    const value = parseDecimal(this.adjustQty());
    return value != null && value !== 0 ? value : null;
  });

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

  protected saved(): void {
    this.adding.set(false);
    this.editing.set(null);
    this.reload.update((n) => n + 1);
    this.changed.emit();
  }

  protected startEdit(medicine: Medicine): void {
    this.adding.set(false);
    this.adjusting.set(null);
    this.editing.set(this.editing() === medicine.id ? null : medicine.id);
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
