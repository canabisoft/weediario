import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { Medicine, MedicineForm, MedicineInput, Origin, TreatmentApi } from '../core/api/treatment.api';
import { formatCompact, parseDecimal } from '../shared/decimal';
import {
  CONCENTRATION_LABEL,
  FORM_LABEL,
  FORM_UNITS,
  ORIGIN_LABEL,
  STOCK_UNIT_LABEL,
  acceptsPercent,
  decimalInput,
  homemadeMgPerMl,
  percentToMg,
} from './treatment-text';

const FORMS = Object.keys(FORM_LABEL) as MedicineForm[];
const ORIGINS = Object.keys(ORIGIN_LABEL) as Origin[];

/**
 * Cadastro e edição de um remédio. Na edição, forma e quantidade inicial ficam travadas: o servidor não muda as duas
 * (correção de estoque vira ajuste, para o histórico de doses continuar batendo).
 * <p>
 * Óleo, tintura e pomada aceitam a concentração em % (convertida para mg/ml ou mg/g antes de salvar) e óleo/tintura
 * têm a conta do óleo caseiro a partir da flor. Tudo é só conta do que a pessoa informou, nunca sugestão de uso.
 */
@Component({
  selector: 'app-medicine-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="wd-card grid gap-3 sm:grid-cols-2" (submit)="$event.preventDefault(); save()"
          [attr.aria-label]="medicine() ? 'Editar remédio' : 'Novo remédio'">
      <div class="sm:col-span-2">
        <label class="wd-label" [for]="id('name')">Nome (como está no frasco)</label>
        <input [id]="id('name')" class="wd-input" maxlength="80" [value]="name()" (input)="name.set($any($event.target).value)"
               placeholder="Ex.: Óleo CBD 20:1 da associação" />
      </div>
      <div>
        <label class="wd-label" [for]="id('form')">Forma</label>
        <select [id]="id('form')" class="wd-input text-base" [value]="form()" [disabled]="!!medicine()"
                (change)="changeForm($any($event.target).value)">
          @for (option of forms; track option) {
            <option [value]="option">{{ formLabel[option] }}</option>
          }
        </select>
        @if (medicine()) {
          <p class="mt-1 text-xs text-fg-muted">A forma não muda depois de cadastrada.</p>
        }
      </div>
      <div>
        <label class="wd-label" [for]="id('origin')">Origem</label>
        <select [id]="id('origin')" class="wd-input text-base" [value]="origin()" (change)="origin.set($any($event.target).value)">
          @for (option of origins; track option) {
            <option [value]="option">{{ originLabel[option] }}</option>
          }
        </select>
      </div>

      @if (percentAllowed()) {
        <fieldset class="flex flex-wrap items-center gap-3 text-sm sm:col-span-2">
          <legend class="sr-only">Unidade da concentração</legend>
          <span class="text-fg-muted">Concentração em:</span>
          <label class="flex items-center gap-1">
            <input type="radio" class="size-4" [name]="id('unit')" [checked]="unit() === 'MG'" (change)="switchUnit('MG')" />
            {{ mgLabel() }}
          </label>
          <label class="flex items-center gap-1">
            <input type="radio" class="size-4" [name]="id('unit')" [checked]="unit() === 'PERCENT'" (change)="switchUnit('PERCENT')" />
            %
          </label>
        </fieldset>
      }
      <div>
        <label class="wd-label" [for]="id('cbd')">CBD ({{ typedUnitLabel() }})</label>
        <input [id]="id('cbd')" class="wd-input" inputmode="decimal" [value]="cbd()" (input)="cbd.set($any($event.target).value)"
               placeholder="do rótulo ou laudo" />
        @if (converted(cbd()); as mg) {
          <p class="mt-1 text-xs text-fg-muted">= {{ mg }}</p>
        }
      </div>
      <div>
        <label class="wd-label" [for]="id('thc')">THC ({{ typedUnitLabel() }})</label>
        <input [id]="id('thc')" class="wd-input" inputmode="decimal" [value]="thc()" (input)="thc.set($any($event.target).value)"
               placeholder="do rótulo ou laudo" />
        @if (converted(thc()); as mg) {
          <p class="mt-1 text-xs text-fg-muted">= {{ mg }}</p>
        }
      </div>
      @if (percentAllowed() && unit() === 'PERCENT') {
        <p class="text-xs text-fg-muted sm:col-span-2">Conversão de rótulo: 1% = 10 {{ mgLabel() }}.</p>
      }

      @if (usesDrops()) {
        <details class="rounded-xl border border-line p-3 sm:col-span-2" [open]="homemadeOpen()" (toggle)="homemadeOpen.set($any($event.target).open)">
          <summary class="cursor-pointer text-sm font-semibold">Fez em casa com a sua flor? Calcular pelo % da planta</summary>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            <div>
              <label class="wd-label" [for]="id('flower')">Flor usada (g)</label>
              <input [id]="id('flower')" class="wd-input" inputmode="decimal" [value]="flowerGrams()" (input)="flowerGrams.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" [for]="id('oil')">Óleo pronto (ml)</label>
              <input [id]="id('oil')" class="wd-input" inputmode="decimal" [value]="oilMl()" (input)="oilMl.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" [for]="id('flower-thc')">THC da flor (%)</label>
              <input [id]="id('flower-thc')" class="wd-input" inputmode="decimal" [value]="flowerThc()" (input)="flowerThc.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" [for]="id('flower-cbd')">CBD da flor (%)</label>
              <input [id]="id('flower-cbd')" class="wd-input" inputmode="decimal" [value]="flowerCbd()" (input)="flowerCbd.set($any($event.target).value)" />
            </div>
          </div>
          @if (homemade(); as result) {
            <p class="mt-3 text-sm" role="status">
              ≈ <strong>{{ result.thc != null ? fmt(result.thc) : '?' }} mg/ml de THC</strong> ·
              <strong>{{ result.cbd != null ? fmt(result.cbd) : '?' }} mg/ml de CBD</strong>
            </p>
            <p class="mt-1 text-xs text-fg-muted">
              Conta pelo teor total da flor, como se tudo passasse para o óleo. A extração sempre perde uma parte, então
              o óleo real tem menos. Se tiver laudo do óleo pronto, prefira os números dele.
            </p>
            <button type="button" class="wd-button mt-2 min-h-9 bg-surface-raised text-fg" (click)="useHomemade()">Usar estes valores</button>
          }
        </details>
      }

      <div>
        <label class="wd-label" [for]="id('qty')">Quantidade no frasco/pote ({{ stockUnit() }})</label>
        <input [id]="id('qty')" class="wd-input" inputmode="decimal" [value]="initial()" [disabled]="!!medicine()"
               (input)="initial.set($any($event.target).value)" required />
        @if (medicine()) {
          <p class="mt-1 text-xs text-fg-muted">Para corrigir o saldo, use "Ajustar estoque".</p>
        }
      </div>
      @if (usesDrops()) {
        <div>
          <label class="wd-label" [for]="id('drops')">Gotas por ml do conta-gotas</label>
          <input [id]="id('drops')" class="wd-input" inputmode="decimal" [value]="drops()" (input)="drops.set($any($event.target).value)" />
          <p class="mt-1 text-xs text-fg-muted">Padrão 20. Para conferir: conte as gotas de 1 ml numa seringa.</p>
        </div>
      }
      <div>
        <label class="wd-label" [for]="id('made')">Fabricação</label>
        <input [id]="id('made')" type="date" class="wd-input text-base" [value]="manufactured()" (input)="manufactured.set($any($event.target).value)" />
      </div>
      <div>
        <label class="wd-label" [for]="id('exp')">Validade</label>
        <input [id]="id('exp')" type="date" class="wd-input text-base" [value]="expires()" (input)="expires.set($any($event.target).value)" />
      </div>
      <div class="sm:col-span-2">
        <label class="wd-label" [for]="id('notes')">Observações</label>
        <textarea [id]="id('notes')" rows="2" maxlength="1000" class="wd-input py-2 text-base" [value]="notes()"
                  (input)="notes.set($any($event.target).value)"></textarea>
      </div>
      @if (error()) {
        <p role="alert" class="text-sm text-danger sm:col-span-2">{{ error() }}</p>
      }
      <div class="flex gap-2 sm:col-span-2">
        <button type="submit" class="wd-button" [disabled]="!canSave() || busy()">
          {{ medicine() ? 'Salvar alterações' : 'Salvar remédio' }}
        </button>
        <button type="button" class="wd-button bg-surface-raised text-fg" (click)="cancelled.emit()">Cancelar</button>
      </div>
    </form>
  `,
})
export class MedicineEditor implements OnInit {
  private readonly api = inject(TreatmentApi);

  readonly patientId = input.required<string>();
  /** Remédio a editar; sem ele, o formulário cadastra um novo. */
  readonly medicine = input<Medicine | null>(null);
  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly forms = FORMS;
  protected readonly origins = ORIGINS;
  protected readonly formLabel = FORM_LABEL;
  protected readonly originLabel = ORIGIN_LABEL;

  protected readonly name = signal('');
  protected readonly form = signal<MedicineForm>('OIL');
  protected readonly origin = signal<Origin>('ASSOCIATION');
  protected readonly thc = signal('');
  protected readonly cbd = signal('');
  /** Como THC e CBD foram digitados: na unidade do rótulo (mg/ml, mg/g) ou em %. */
  protected readonly unit = signal<'MG' | 'PERCENT'>('MG');
  protected readonly initial = signal('');
  protected readonly drops = signal('20');
  protected readonly manufactured = signal('');
  protected readonly expires = signal('');
  protected readonly notes = signal('');

  protected readonly homemadeOpen = signal(false);
  protected readonly flowerGrams = signal('');
  protected readonly oilMl = signal('');
  protected readonly flowerThc = signal('');
  protected readonly flowerCbd = signal('');

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly percentAllowed = computed(() => acceptsPercent(this.form()));
  protected readonly mgLabel = computed(() => CONCENTRATION_LABEL[FORM_UNITS[this.form()].concentration]);
  protected readonly typedUnitLabel = computed(() => (this.percentAllowed() && this.unit() === 'PERCENT' ? '%' : this.mgLabel()));
  protected readonly stockUnit = computed(() => STOCK_UNIT_LABEL[FORM_UNITS[this.form()].stock]);
  protected readonly usesDrops = computed(() => FORM_UNITS[this.form()].dose.includes('DROP'));
  protected readonly canSave = computed(() => !!this.name().trim() && (parseDecimal(this.initial()) ?? 0) > 0);

  protected readonly homemade = computed(() => {
    const grams = parseDecimal(this.flowerGrams());
    const ml = parseDecimal(this.oilMl());
    if (grams == null || ml == null) {
      return null;
    }
    const of = (text: string) => {
      const percent = parseDecimal(text);
      return percent == null ? null : homemadeMgPerMl(grams, percent, ml);
    };
    const result = { thc: of(this.flowerThc()), cbd: of(this.flowerCbd()) };
    return result.thc == null && result.cbd == null ? null : result;
  });

  ngOnInit(): void {
    const medicine = this.medicine();
    if (!medicine) {
      return;
    }
    this.name.set(medicine.name);
    this.form.set(medicine.form);
    this.origin.set(medicine.origin);
    this.thc.set(decimalInput(medicine.thc));
    this.cbd.set(decimalInput(medicine.cbd));
    this.initial.set(decimalInput(medicine.initialQuantity));
    this.drops.set(decimalInput(medicine.dropsPerMl ?? 20));
    this.manufactured.set(medicine.manufacturedOn ?? '');
    this.expires.set(medicine.expiresOn ?? '');
    this.notes.set(medicine.notes ?? '');
  }

  protected id(field: string): string {
    return `med-${this.medicine()?.id ?? 'new'}-${field}`;
  }

  protected fmt(value: number): string {
    return formatCompact(value, 3);
  }

  /** Texto "30 mg/ml" do valor digitado em %; nada quando já está na unidade do rótulo. */
  protected converted(text: string): string | null {
    if (!this.percentAllowed() || this.unit() !== 'PERCENT') {
      return null;
    }
    const percent = parseDecimal(text);
    return percent == null ? null : `${this.fmt(percentToMg(percent))} ${this.mgLabel()}`;
  }

  /** Forma sem % (goma, cápsula, flor): o que foi digitado em % passa para a unidade do rótulo antes da troca. */
  protected changeForm(next: MedicineForm): void {
    if (this.unit() === 'PERCENT' && !acceptsPercent(next)) {
      this.switchUnit('MG');
    }
    this.form.set(next);
  }

  /** Troca a unidade convertendo o que já foi digitado, para o número não mudar de sentido. */
  protected switchUnit(next: 'MG' | 'PERCENT'): void {
    if (next === this.unit()) {
      return;
    }
    const convert = (text: string) => {
      const value = parseDecimal(text);
      return value == null ? text : decimalInput(next === 'PERCENT' ? value / 10 : percentToMg(value));
    };
    this.thc.set(convert(this.thc()));
    this.cbd.set(convert(this.cbd()));
    this.unit.set(next);
  }

  protected useHomemade(): void {
    const result = this.homemade();
    if (!result) {
      return;
    }
    this.unit.set('MG');
    this.thc.set(decimalInput(result.thc));
    this.cbd.set(decimalInput(result.cbd));
    if (!this.medicine() && !this.initial().trim()) {
      this.initial.set(this.oilMl());
    }
    if (this.origin() === 'ASSOCIATION') {
      this.origin.set('HOME_GROWN');
    }
    this.homemadeOpen.set(false);
  }

  protected save(): void {
    const medicine = this.medicine();
    const input: MedicineInput = {
      name: this.name().trim(),
      form: this.form(),
      origin: this.origin(),
      thc: this.stored(this.thc()),
      cbd: this.stored(this.cbd()),
      dropsPerMl: this.usesDrops() ? (parseDecimal(this.drops()) ?? undefined) : undefined,
      initialQuantity: medicine ? medicine.initialQuantity : (parseDecimal(this.initial()) ?? 0),
      manufacturedOn: this.manufactured() || undefined,
      expiresOn: this.expires() || undefined,
      notes: this.notes().trim() || undefined,
    };
    const request: Observable<unknown> = medicine
      ? this.api.updateMedicine(this.patientId(), medicine.id, input)
      : this.api.registerMedicine(this.patientId(), input);
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
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

  /** Valor como o servidor guarda: % vira mg/ml (ou mg/g); campo vazio limpa a concentração. */
  private stored(text: string): number | undefined {
    const value = parseDecimal(text);
    if (value == null) {
      return undefined;
    }
    return this.percentAllowed() && this.unit() === 'PERCENT' ? percentToMg(value) : value;
  }
}
