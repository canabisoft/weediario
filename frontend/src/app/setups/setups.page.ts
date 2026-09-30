import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { GrowEnvironment, GrowMedium, GrowSetup, ProductSnapshot } from '../core/api/api.models';
import { AccountApi, SetupInput } from '../core/api/account.api';
import { problemMessage } from '../core/api/problem';
import { Icon } from '../shared/icon';
import { ProductListEditor } from '../shared/product-list-editor';
import { ENVIRONMENT_LABEL, MEDIUM_LABEL, categoryLabel, options } from '../shared/labels';

/**
 * Equipamentos: setups salvos do grower (tenda, luz, substrato...) para reutilizar a cada novo diário.
 * Só o próprio grower vê esta lista.
 */
@Component({
  selector: 'app-setups-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, Icon, ProductListEditor],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">Equipamentos</h1>
        <p class="text-fg-muted">Seus setups salvos. Ao iniciar um diário, escolha um e ele é copiado com um toque.</p>
      </div>
      @if (editing() === null) {
        <button type="button" class="wd-button" (click)="startCreate()"><app-icon name="plus" class="size-5" /> Novo setup</button>
      }
    </div>

    @if (editing() !== null) {
      <form class="wd-card mt-4 grid gap-4" [formGroup]="form" (ngSubmit)="save()">
        <h2 class="text-lg font-semibold">{{ editing() === 'new' ? 'Novo setup' : 'Editar setup' }}</h2>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="wd-label" for="s-name">Nome</label>
            <input id="s-name" class="wd-input" formControlName="name" maxlength="60" placeholder="Ex.: Tenda 80x80" />
          </div>
          <div>
            <label class="wd-label" for="s-env">Ambiente</label>
            <select id="s-env" class="wd-input" formControlName="environment">
              @for (o of environmentOptions; track o.value) {
                <option [value]="o.value">{{ o.label }}</option>
              }
            </select>
          </div>
        </div>
        <fieldset>
          <legend class="wd-label">Tenda (cm)</legend>
          <div class="grid grid-cols-3 gap-2">
            <input aria-label="Largura" type="number" inputmode="numeric" min="10" max="1000" class="wd-input" formControlName="tentWidthCm" placeholder="Larg." />
            <input aria-label="Profundidade" type="number" inputmode="numeric" min="10" max="1000" class="wd-input" formControlName="tentDepthCm" placeholder="Prof." />
            <input aria-label="Altura" type="number" inputmode="numeric" min="10" max="1000" class="wd-input" formControlName="tentHeightCm" placeholder="Alt." />
          </div>
        </fieldset>
        <div class="grid gap-4 sm:grid-cols-[1fr_8rem]">
          <div>
            <label class="wd-label" for="s-light">Iluminação</label>
            <input id="s-light" class="wd-input" formControlName="lighting" maxlength="120" placeholder="Ex.: LED Quantum Board" />
          </div>
          <div>
            <label class="wd-label" for="s-watt">Potência (W)</label>
            <input id="s-watt" type="number" inputmode="numeric" min="1" max="5000" class="wd-input" formControlName="lightWattage" />
          </div>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="wd-label" for="s-medium">Meio de cultivo</label>
            <select id="s-medium" class="wd-input" formControlName="medium">
              <option value="">Não informar</option>
              @for (o of mediumOptions; track o.value) {
                <option [value]="o.value">{{ o.label }}</option>
              }
            </select>
          </div>
        </div>
        <div>
          <label class="wd-label" for="s-vent">Ventilação</label>
          <input id="s-vent" class="wd-input" formControlName="ventilation" maxlength="120" placeholder="Ex.: Exaustor 100mm + ventilador clip" />
        </div>
        <fieldset class="grid gap-3 rounded-xl border border-line p-3">
          <legend class="px-1 font-semibold">Produtos (fertilizantes, substratos...)</legend>
          @if (form.controls.nutrients.value; as legacy) {
            <div class="rounded-lg bg-surface-raised p-3 text-sm">
              <p>
                Anotação antiga de nutrientes: <strong>{{ legacy }}</strong>. Ela não conta no ranking: cadastre os
                nutrientes abaixo como produtos.
              </p>
              <button type="button" class="mt-1 text-accent underline" (click)="form.controls.nutrients.setValue('')">Apagar anotação</button>
            </div>
          }
          <app-product-list-editor [(products)]="products" />
        </fieldset>
        <div>
          <label class="wd-label" for="s-notes">Observações</label>
          <textarea id="s-notes" rows="3" maxlength="1000" class="wd-input py-2 text-base" formControlName="notes"></textarea>
        </div>
        @if (error()) {
          <p role="alert" class="text-sm text-danger">{{ error() }}</p>
        }
        <div class="flex gap-2">
          <button type="submit" class="wd-button" [disabled]="form.invalid || busy()">Salvar</button>
          <button type="button" class="wd-button bg-surface-raised text-fg" (click)="editing.set(null)">Cancelar</button>
        </div>
      </form>
    }

    <ul class="mt-4 grid gap-3 sm:grid-cols-2">
      @for (setup of setups(); track setup.id) {
        <li class="wd-card">
          <p class="flex items-start justify-between gap-2">
            <span>
              <span class="block font-semibold">{{ setup.name }}</span>
              <span class="text-sm text-fg-muted">{{ environment[setup.environment] }}{{ setup.spec.medium ? ' · ' + medium[setup.spec.medium] : '' }}</span>
            </span>
            <span class="flex gap-1">
              <button type="button" class="grid size-10 place-items-center rounded-lg text-fg-muted" aria-label="Editar" (click)="startEdit(setup)"><app-icon name="edit" class="size-5" /></button>
              <button type="button" class="grid size-10 place-items-center rounded-lg text-fg-muted" aria-label="Apagar" (click)="remove(setup)"><app-icon name="trash" class="size-5" /></button>
            </span>
          </p>
          <ul class="mt-2 text-sm text-fg-muted">
            @if (setup.spec.tentWidthCm) {
              <li>Tenda {{ setup.spec.tentWidthCm }}×{{ setup.spec.tentDepthCm ?? '?' }}×{{ setup.spec.tentHeightCm ?? '?' }} cm</li>
            }
            @if (setup.spec.lighting) {
              <li>{{ setup.spec.lighting }}{{ setup.spec.lightWattage ? ' · ' + setup.spec.lightWattage + ' W' : '' }}</li>
            }
            @if (setup.spec.nutrients) {
              <li>{{ setup.spec.nutrients }}</li>
            }
            @if (setup.spec.ventilation) {
              <li>{{ setup.spec.ventilation }}</li>
            }
            @for (product of setup.products ?? []; track product.id) {
              <li>{{ categoryLabel(product.category) }}: {{ product.brand }} · {{ product.name }}</li>
            }
          </ul>
        </li>
      } @empty {
        @if (editing() === null) {
          <li class="wd-card text-center text-fg-muted sm:col-span-2">Você ainda não salvou nenhum setup.</li>
        }
      }
    </ul>
  `,
})
export class SetupsPage {
  private readonly api = inject(AccountApi);

  protected readonly environment = ENVIRONMENT_LABEL;
  protected readonly medium = MEDIUM_LABEL;
  protected readonly environmentOptions = options(ENVIRONMENT_LABEL);
  protected readonly mediumOptions = options(MEDIUM_LABEL);
  protected readonly categoryLabel = categoryLabel;

  /** Produtos do setup em edição. */
  protected readonly products = signal<ProductSnapshot[]>([]);

  protected readonly setups = signal<GrowSetup[]>([]);
  /** null = lista; 'new' = criando; id = editando. */
  protected readonly editing = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    environment: ['INDOOR' as GrowEnvironment],
    tentWidthCm: [null as number | null],
    tentDepthCm: [null as number | null],
    tentHeightCm: [null as number | null],
    lighting: [''],
    lightWattage: [null as number | null],
    medium: ['' as GrowMedium | ''],
    nutrients: [''],
    ventilation: [''],
    notes: [''],
  });

  constructor() {
    this.load();
  }

  protected startCreate(): void {
    this.form.reset({ environment: 'INDOOR', medium: '' });
    this.products.set([]);
    this.error.set(null);
    this.editing.set('new');
  }

  protected startEdit(setup: GrowSetup): void {
    this.form.reset({ name: setup.name, environment: setup.environment, ...setup.spec, medium: setup.spec.medium ?? '' });
    this.products.set(setup.products ?? []);
    this.error.set(null);
    this.editing.set(setup.id);
  }

  protected save(): void {
    const value = this.form.getRawValue();
    const input = {
      ...value,
      medium: value.medium || undefined,
      productIds: this.products().map((product) => product.id),
    } as SetupInput;
    const id = this.editing();
    this.busy.set(true);
    (id === 'new' || id === null ? this.api.createSetup(input) : this.api.updateSetup(id, input)).subscribe({
      next: () => {
        this.editing.set(null);
        this.busy.set(false);
        this.load();
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected remove(setup: GrowSetup): void {
    if (confirm(`Apagar o setup "${setup.name}"? Diários já criados com ele não mudam.`)) {
      this.api.deleteSetup(setup.id).subscribe(() => this.load());
    }
  }

  private load(): void {
    this.api.setups().subscribe((setups) => this.setups.set(setups));
  }
}
