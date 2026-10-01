import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { CatalogItem } from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import {
  IngredientUnit,
  RecipeApplication,
  RecipeInput,
  RecipeKind,
  RecipeStage,
  RecipeView,
  RecipeVisibility,
  RecipesApi,
} from '../core/api/recipes.api';
import { CatalogPicker } from '../shared/catalog-picker';
import { Icon } from '../shared/icon';
import { APPLICATION_LABEL, COMMON_INGREDIENTS, STAGE_LABEL, UNIT_LABEL, isSolution } from './recipe-labels';

interface Row {
  readonly key: number;
  readonly mode: 'brand' | 'generic';
  /** Produto já escolhido (ao editar, ou depois de escolher marca → produto). */
  readonly product: { id: string; name: string; brand: string } | null;
  readonly brand: CatalogItem | null;
  readonly name: string;
  readonly brandText: string;
  readonly quantity: number | null;
  readonly unit: IngredientUnit;
}

const DEFAULT_UNIT: Record<RecipeKind, IngredientUnit> = { MINERAL: 'G', ORGANIC: 'KG' };

/**
 * Criar ou editar uma receita. Cada ingrediente é um produto de marca do catálogo (conta em "Produtos") ou um
 * sal/insumo genérico pelo nome, com marca opcional. Com {@code ?diario=<id>}, a receita nova já entra no cultivo.
 */
@Component({
  selector: 'app-recipe-editor-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, CatalogPicker],
  template: `
    <a [routerLink]="id() ? ['/receitas', id()] : ['/receitas']" class="text-sm text-fg-muted">← {{ id() ? 'Voltar à receita' : 'Receitas' }}</a>
    <h1 class="mt-2 text-2xl font-bold">{{ id() ? 'Editar receita' : 'Nova receita' }}</h1>
    @if (diario()) {
      <p class="mt-1 text-sm text-fg-muted">Ao salvar, a receita passa a aparecer no seu cultivo.</p>
    }

    <form class="mt-4 grid gap-4" (ngSubmit)="save()">
      <div>
        <label class="wd-label" for="r-name">Nome da receita</label>
        <input id="r-name" class="wd-input" maxlength="80" required placeholder="Ex.: Flora semana 3, Super solo 100 L"
               [(ngModel)]="name" name="name" />
      </div>

      <fieldset>
        <legend class="wd-label">Tipo</legend>
        <div class="grid grid-cols-2 gap-2">
          @for (option of kinds; track option.id) {
            <label class="flex min-h-12 cursor-pointer items-center gap-2 rounded-xl border px-3"
                   [class]="kind() === option.id ? 'border-accent bg-accent/10' : 'border-line'">
              <input type="radio" name="kind" class="accent-[var(--wd-accent)]" [value]="option.id"
                     [checked]="kind() === option.id" (change)="setKind(option.id)" />
              <span><span class="block font-semibold">{{ option.label }}</span><span class="text-xs text-fg-muted">{{ option.hint }}</span></span>
            </label>
          }
        </div>
      </fieldset>

      <div class="grid gap-4 sm:grid-cols-3">
        <div>
          <label class="wd-label" for="r-app">Aplicação</label>
          <select id="r-app" class="wd-input" [ngModel]="application()" (ngModelChange)="setApplication($event)" name="application">
            @for (o of applicationOptions; track o.value) {
              <option [value]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
        <div>
          <label class="wd-label" for="r-batch">Rende (litros {{ solutionLike() ? 'de água' : 'de substrato' }})</label>
          <input id="r-batch" type="number" inputmode="decimal" min="0.1" max="10000" step="0.1" required class="wd-input"
                 [(ngModel)]="batchLiters" name="batchLiters" />
        </div>
        <div>
          <label class="wd-label" for="r-stage">Fase</label>
          <select id="r-stage" class="wd-input" [(ngModel)]="stage" name="stage">
            <option [ngValue]="undefined">Não informar</option>
            @for (o of stageOptions; track o.value) {
              <option [ngValue]="o.value">{{ o.label }}</option>
            }
          </select>
        </div>
      </div>

      @if (isSolution(application())) {
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="wd-label" for="r-ec">EC alvo (mS/cm)</label>
            <input id="r-ec" type="number" inputmode="decimal" min="0" max="10" step="0.01" class="wd-input" [(ngModel)]="targetEc" name="targetEc" />
          </div>
          <div>
            <label class="wd-label" for="r-ph">pH alvo</label>
            <input id="r-ph" type="number" inputmode="decimal" min="0" max="14" step="0.1" class="wd-input" [(ngModel)]="targetPh" name="targetPh" />
          </div>
        </div>
      }

      <fieldset class="grid gap-3 rounded-xl border border-line p-3">
        <legend class="px-1 font-semibold">{{ kind() === 'MINERAL' ? 'Sais e fertilizantes' : 'Insumos' }}</legend>
        <datalist id="common-ingredients">
          @for (option of ingredientOptions(); track option) {
            <option [value]="option"></option>
          }
        </datalist>
        @for (row of rows(); track row.key; let i = $index) {
          <div class="grid gap-2 rounded-lg bg-surface-raised p-3">
            <div class="flex items-center gap-2 text-sm">
              <span class="font-semibold">{{ i + 1 }}.</span>
              <button type="button" class="rounded-full px-3 py-1" [class]="row.mode === 'generic' ? 'bg-accent text-accent-fg' : 'bg-surface'"
                      (click)="patch(i, { mode: 'generic', product: null, brand: null })">Sal / insumo</button>
              <button type="button" class="rounded-full px-3 py-1" [class]="row.mode === 'brand' ? 'bg-accent text-accent-fg' : 'bg-surface'"
                      (click)="patch(i, { mode: 'brand' })">Produto de marca</button>
              <button type="button" class="ml-auto grid size-9 place-items-center rounded-lg text-fg-muted" [attr.aria-label]="'Remover ingrediente ' + (i + 1)"
                      [disabled]="rows().length === 1" (click)="remove(i)">
                <app-icon name="trash" class="size-4" />
              </button>
            </div>

            @if (row.mode === 'generic') {
              <div class="grid gap-2 sm:grid-cols-2">
                <div>
                  <label class="wd-label" [for]="'ing-name-' + row.key">Ingrediente</label>
                  <input [id]="'ing-name-' + row.key" class="wd-input" maxlength="80" list="common-ingredients"
                         [placeholder]="kind() === 'MINERAL' ? 'Ex.: Nitrato de cálcio' : 'Ex.: Húmus de minhoca'"
                         [ngModel]="row.name" (ngModelChange)="patch(i, { name: $event }); typed$.next($event)" [name]="'name-' + row.key" />
                </div>
                <div>
                  <label class="wd-label" [for]="'ing-brand-' + row.key">Marca (opcional)</label>
                  <input [id]="'ing-brand-' + row.key" class="wd-input" maxlength="80" placeholder="Ex.: Yara, Genérico"
                         [ngModel]="row.brandText" (ngModelChange)="patch(i, { brandText: $event })" [name]="'brand-' + row.key" />
                </div>
              </div>
            } @else if (row.product; as product) {
              <div class="flex min-h-12 items-center gap-2 rounded-xl border border-accent/50 bg-accent/10 px-3">
                <app-icon name="check" class="size-4 text-accent" />
                <span class="min-w-0 flex-1 truncate"><span class="font-semibold">{{ product.name }}</span> <span class="text-sm text-fg-muted">· {{ product.brand }}</span></span>
                <button type="button" class="text-sm text-accent" (click)="patch(i, { product: null })">Trocar</button>
              </div>
            } @else {
              <app-catalog-picker kind="brands" label="Marca" placeholder="Ex.: Advanced Nutrients"
                                  [value]="row.brand" (valueChange)="patch(i, { brand: $event })" />
              @if (row.brand; as brand) {
                <app-catalog-picker kind="products" label="Produto" placeholder="Ex.: Sensi Bloom A" [brandId]="brand.id" category="NUTRIENT"
                                    [value]="null" (valueChange)="pickProduct(i, $event)" />
              }
            }

            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="wd-label" [for]="'ing-qty-' + row.key">Quantidade</label>
                <input [id]="'ing-qty-' + row.key" type="number" inputmode="decimal" min="0.001" step="any" class="wd-input"
                       [ngModel]="row.quantity" (ngModelChange)="patch(i, { quantity: $event })" [name]="'qty-' + row.key" />
              </div>
              <div>
                <label class="wd-label" [for]="'ing-unit-' + row.key">Unidade</label>
                <select [id]="'ing-unit-' + row.key" class="wd-input" [ngModel]="row.unit" (ngModelChange)="patch(i, { unit: $event })" [name]="'unit-' + row.key">
                  @for (o of unitOptions; track o.value) {
                    <option [value]="o.value">{{ o.label }}</option>
                  }
                </select>
              </div>
            </div>
          </div>
        }
        <button type="button" class="wd-button bg-surface text-fg" [disabled]="rows().length >= 30" (click)="add()">
          <app-icon name="plus" class="size-5" /> Adicionar ingrediente
        </button>
        <p class="text-xs text-fg-muted">
          Quantidades valem para o rendimento informado acima. Produtos de marca entram no catálogo da comunidade e
          contam no marcador de receitas em Produtos.
        </p>
      </fieldset>

      <div>
        <label class="wd-label" for="r-notes">Modo de preparo e observações</label>
        <textarea id="r-notes" rows="4" maxlength="2000" class="wd-input py-2 text-base"
                  placeholder="Ex.: ordem de mistura, tempo de aeração, intervalo entre aplicações" [(ngModel)]="notes" name="notes"></textarea>
      </div>

      <label class="flex items-start gap-3 rounded-xl border border-line p-3">
        <input type="checkbox" class="mt-1 size-5" [checked]="visibility() === 'PUBLIC'"
               (change)="visibility.set($any($event.target).checked ? 'PUBLIC' : 'PRIVATE')" />
        <span>
          <span class="block font-semibold">Receita pública</span>
          <span class="text-sm text-fg-muted">Aparece no seu perfil e outros growers podem replicá-la. Privada, só você vê.</span>
        </span>
      </label>

      @if (error()) {
        <p role="alert" class="text-sm text-danger">{{ error() }}</p>
      }
      <button type="submit" class="wd-button" [disabled]="busy()">{{ id() ? 'Salvar alterações' : 'Criar receita' }}</button>
    </form>
  `,
})
export class RecipeEditorPage {
  private readonly api = inject(RecipesApi);
  private readonly router = inject(Router);

  /** Rota de edição (/receitas/:id/editar). */
  readonly id = input<string | undefined>();
  /** Cultivo onde a receita nova deve entrar. */
  readonly diario = input<string | undefined>();

  protected readonly kinds: readonly { id: RecipeKind; label: string; hint: string }[] = [
    { id: 'MINERAL', label: 'Mineral', hint: 'Sais e fertilizantes' },
    { id: 'ORGANIC', label: 'Orgânica', hint: 'Solo vivo, chás, guanos' },
  ];
  protected readonly applicationOptions = options(APPLICATION_LABEL);
  protected readonly stageOptions = options(STAGE_LABEL);
  protected readonly unitOptions = options(UNIT_LABEL);
  protected readonly isSolution = isSolution;

  protected name = '';
  protected batchLiters: number | null = 10;
  protected stage: RecipeStage | undefined;
  protected targetEc: number | null = null;
  protected targetPh: number | null = null;
  protected notes = '';
  protected readonly kind = signal<RecipeKind>('MINERAL');
  protected readonly application = signal<RecipeApplication>('FERTIGATION');
  protected readonly visibility = signal<RecipeVisibility>('PUBLIC');
  protected readonly rows = signal<Row[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly solutionLike = computed(() => !['SOIL_MIX', 'TOP_DRESS'].includes(this.application()));

  protected readonly typed$ = new Subject<string>();
  private readonly community = toSignal(
    this.typed$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => (q.trim().length < 2 ? of([]) : this.api.suggestIngredients(q).pipe(catchError(() => of([]))))),
    ),
    { initialValue: [] as string[] },
  );
  protected readonly ingredientOptions = computed(() => [
    ...new Set([...this.community(), ...COMMON_INGREDIENTS[this.kind()]]),
  ]);

  private nextKey = 0;

  constructor() {
    this.rows.set([this.emptyRow()]);
    effect(() => {
      const id = this.id();
      if (id) {
        untracked(() => this.load(id));
      }
    });
  }

  protected setKind(kind: RecipeKind): void {
    this.kind.set(kind);
    if (!this.id()) {
      this.setApplication(kind === 'MINERAL' ? 'FERTIGATION' : 'SOIL_MIX');
      this.batchLiters = kind === 'MINERAL' ? 10 : 100;
      this.rows.update((rows) => rows.map((row) => (row.quantity == null ? { ...row, unit: DEFAULT_UNIT[kind] } : row)));
    }
  }

  protected setApplication(application: RecipeApplication): void {
    this.application.set(application);
    if (!isSolution(application)) {
      this.targetEc = null;
      this.targetPh = null;
    }
  }

  protected patch(index: number, changes: Partial<Row>): void {
    this.rows.update((rows) => rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));
  }

  protected pickProduct(index: number, item: CatalogItem | null): void {
    if (item) {
      this.patch(index, { product: { id: item.id, name: item.name, brand: item.parentName ?? this.rows()[index].brand?.name ?? '' } });
    }
  }

  protected add(): void {
    this.rows.update((rows) => [...rows, this.emptyRow()]);
  }

  protected remove(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected save(): void {
    const input = this.toInput();
    if (typeof input === 'string') {
      this.error.set(input);
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    const id = this.id();
    const request: Observable<RecipeView> = id ? this.api.update(id, input) : this.api.create(input);
    request
      .pipe(switchMap((saved) => (id || !this.diario() ? of(saved) : this.attachToDiary(this.diario()!, saved))))
      .subscribe({
        next: (saved) => {
          this.busy.set(false);
          this.router.navigate(this.diario() && !id ? ['/diarios', this.diario()] : ['/receitas', saved.id]);
        },
        error: (error) => {
          this.error.set(problemMessage(error));
          this.busy.set(false);
        },
      });
  }

  /** Mantém as receitas que o cultivo já tinha e acrescenta a nova. */
  private attachToDiary(diaryId: string, saved: RecipeView): Observable<RecipeView> {
    return this.api.ofDiary(diaryId).pipe(
      catchError(() => of([])),
      switchMap((current) => this.api.setDiaryRecipes(diaryId, [...current.map((r) => r.id), saved.id])),
      switchMap(() => of(saved)),
    );
  }

  private toInput(): RecipeInput | string {
    if (!this.name.trim()) {
      return 'Dê um nome para a receita.';
    }
    if (!this.batchLiters || this.batchLiters <= 0) {
      return 'Informe quantos litros a receita rende.';
    }
    const ingredients: RecipeInput['ingredients'] = [];
    for (const [i, row] of this.rows().entries()) {
      if (!row.quantity || row.quantity <= 0) {
        return `Informe a quantidade do ingrediente ${i + 1}.`;
      }
      if (row.mode === 'brand') {
        if (!row.product) {
          return `Escolha a marca e o produto do ingrediente ${i + 1}.`;
        }
        ingredients.push({ productId: row.product.id, quantity: row.quantity, unit: row.unit });
      } else {
        if (!row.name.trim()) {
          return `Informe o nome do ingrediente ${i + 1}.`;
        }
        ingredients.push({ name: row.name.trim(), brand: row.brandText.trim() || undefined, quantity: row.quantity, unit: row.unit });
      }
    }
    const solution = isSolution(this.application());
    return {
      name: this.name.trim(),
      kind: this.kind(),
      application: this.application(),
      stage: this.stage,
      batchLiters: this.batchLiters,
      targetEc: solution && this.targetEc != null ? this.targetEc : undefined,
      targetPh: solution && this.targetPh != null ? this.targetPh : undefined,
      notes: this.notes.trim() || undefined,
      visibility: this.visibility(),
      ingredients,
    };
  }

  private load(id: string): void {
    this.api.get(id).subscribe({
      next: ({ recipe, ownedByViewer }) => {
        if (!ownedByViewer) {
          this.router.navigate(['/receitas', id]);
          return;
        }
        this.name = recipe.name;
        this.kind.set(recipe.kind);
        this.application.set(recipe.application);
        this.batchLiters = recipe.batchLiters;
        this.stage = recipe.stage;
        this.targetEc = recipe.targetEc ?? null;
        this.targetPh = recipe.targetPh ?? null;
        this.notes = recipe.notes ?? '';
        this.visibility.set(recipe.visibility);
        this.rows.set(
          recipe.ingredients.map((ingredient) => ({
            key: this.nextKey++,
            mode: ingredient.productId ? 'brand' : 'generic',
            product: ingredient.productId ? { id: ingredient.productId, name: ingredient.name, brand: ingredient.brand ?? '' } : null,
            brand: null,
            name: ingredient.productId ? '' : ingredient.name,
            brandText: ingredient.productId ? '' : (ingredient.brand ?? ''),
            quantity: ingredient.quantity,
            unit: ingredient.unit,
          })),
        );
      },
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  private emptyRow(): Row {
    return {
      key: this.nextKey++,
      mode: 'generic',
      product: null,
      brand: null,
      name: '',
      brandText: '',
      quantity: null,
      unit: DEFAULT_UNIT[this.kind()],
    };
  }
}

function options<K extends string>(labels: Record<K, string>): { value: K; label: string }[] {
  return (Object.entries(labels) as [K, string][]).map(([value, label]) => ({ value, label }));
}
