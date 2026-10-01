import { ChangeDetectionStrategy, Component, computed, inject, input, model, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { CatalogDbApi, CatalogItem, CatalogKind, ProductCategory } from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import { Icon } from './icon';
import { SOURCE_LABEL } from './labels';

let nextId = 0;

/**
 * Campo com autocomplete do catálogo da comunidade (padrão ARIA combobox).
 * <p>
 * Enquanto a pessoa digita, sugere itens existentes. Se nada servir, "Adicionar" cadastra o nome, mas antes
 * o servidor confere se já existe (mesmo nome com outra grafia) ou algo muito parecido ("você quis dizer?"),
 * para o catálogo não ganhar duplicatas.
 */
@Component({
  selector: 'app-catalog-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <label class="wd-label" [for]="inputId">{{ label() }}</label>

    @if (value(); as selected) {
      <div class="flex min-h-12 items-center gap-2 rounded-xl border border-accent/50 bg-accent/10 px-3">
        <app-icon name="check" class="size-4 text-accent" />
        <span class="min-w-0 flex-1 truncate">
          <span class="font-semibold">{{ selected.name }}</span>
          @if (selected.parentName) {
            <span class="text-sm text-fg-muted"> · {{ selected.parentName }}</span>
          }
        </span>
        <button type="button" class="grid size-9 place-items-center rounded-lg text-fg-muted" [attr.aria-label]="'Trocar ' + label()" (click)="clear()">
          <app-icon name="close" class="size-4" />
        </button>
      </div>
    } @else {
      <div class="relative">
        <input
          [id]="inputId"
          class="wd-input"
          role="combobox"
          autocomplete="off"
          maxlength="80"
          aria-autocomplete="list"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="listId"
          [attr.aria-activedescendant]="open() && active() >= 0 ? listId + '-' + active() : null"
          [placeholder]="placeholder()"
          [value]="query()"
          (input)="onType($any($event.target).value)"
          (focus)="open.set(query().trim().length >= 2)"
          (blur)="closeSoon()"
          (keydown)="onKey($event)"
        />
        @if (open() && query().trim().length >= 2) {
          <ul [id]="listId" role="listbox" class="absolute inset-x-0 top-full z-20 mt-1 max-h-72 overflow-auto rounded-xl border border-line bg-surface p-1 shadow-lg">
            @for (item of suggestions(); track item.id; let i = $index) {
              <li
                [id]="listId + '-' + i"
                role="option"
                [attr.aria-selected]="active() === i"
                class="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2"
                [class.bg-surface-raised]="active() === i"
                (mousedown)="$event.preventDefault(); pick(item)"
              >
                <span class="min-w-0 flex-1">
                  <span class="block truncate font-medium">{{ item.name }}</span>
                  <span class="block truncate text-xs text-fg-muted">
                    {{ item.parentName ?? 'sem breeder informado' }} · {{ source[item.source] }}{{ item.status === 'VERIFIED' ? ' · verificado' : item.status === 'TESTING' ? ' · em teste' : '' }}
                  </span>
                </span>
                @if (item.usage) {
                  <span class="text-xs text-fg-muted">{{ item.usage }} {{ item.usage === 1 ? 'uso' : 'usos' }}</span>
                }
              </li>
            }
            @if (allowCreate()) {
              <li
                [id]="listId + '-' + suggestions().length"
                role="option"
                [attr.aria-selected]="active() === suggestions().length"
                class="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-accent"
                [class.bg-surface-raised]="active() === suggestions().length"
                (mousedown)="$event.preventDefault(); create(false)"
              >
                <app-icon name="plus" class="size-4" /> Adicionar "{{ query().trim() }}"
              </li>
            }
          </ul>
        }
      </div>
    }

    @if (candidates().length) {
      <div class="mt-2 rounded-xl border border-warn/40 bg-warn/10 p-3 text-sm" role="alert">
        <p class="font-semibold">Você quis dizer:</p>
        <ul class="mt-2 grid gap-1">
          @for (item of candidates(); track item.id) {
            <li>
              <button type="button" class="w-full rounded-lg bg-surface px-3 py-2 text-left" (click)="pick(item)">
                {{ item.name }}<span class="text-fg-muted">{{ item.parentName ? ' · ' + item.parentName : '' }}</span>
              </button>
            </li>
          }
        </ul>
        <button type="button" class="mt-2 text-accent underline" (click)="create(true)">
          Não, é outro: cadastrar "{{ query().trim() }}"
        </button>
      </div>
    }
    @if (error()) {
      <p role="alert" class="mt-1 text-sm text-danger">{{ error() }}</p>
    }
    @if (hint() && !value()) {
      <p class="mt-1 text-xs text-fg-muted">{{ hint() }}</p>
    }
  `,
})
export class CatalogPicker {
  private readonly api = inject(CatalogDbApi);

  readonly kind = input.required<CatalogKind>();
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly hint = input('');
  /** Filtra strains de um breeder e cadastra a strain já ligada a ele. */
  readonly breederId = input<string | undefined>();
  /** Filtra produtos de uma marca (obrigatório para cadastrar produto). */
  readonly brandId = input<string | undefined>();
  /** Categoria usada ao cadastrar um produto novo. */
  readonly category = input<ProductCategory | undefined>();
  /** Falso quando só vale escolher algo que já existe (ex.: alvo de uma junção na moderação). */
  readonly allowCreate = input(true);
  readonly value = model<CatalogItem | null>(null);

  protected readonly inputId = `catalog-picker-${nextId++}`;
  protected readonly listId = `${this.inputId}-list`;
  protected readonly source = SOURCE_LABEL;
  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly active = signal(-1);
  protected readonly candidates = signal<CatalogItem[]>([]);
  protected readonly error = signal<string | null>(null);

  private readonly search = computed(() => ({
    q: this.query().trim(),
    kind: this.kind(),
    breederId: this.breederId(),
    brandId: this.brandId(),
  }));

  protected readonly suggestions = toSignal(
    toObservable(this.search).pipe(
      debounceTime(250),
      distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      switchMap(({ q, kind, breederId, brandId }) =>
        q.length < 2
          ? of<CatalogItem[]>([])
          : this.api.suggest(kind, q, { breederId, brandId }).pipe(catchError(() => of<CatalogItem[]>([]))),
      ),
      map((items) => items.slice(0, 8)),
    ),
    { initialValue: [] },
  );

  protected onType(text: string): void {
    this.query.set(text);
    this.candidates.set([]);
    this.error.set(null);
    this.active.set(-1);
    this.open.set(text.trim().length >= 2);
  }

  protected onKey(event: KeyboardEvent): void {
    // A opção "Adicionar" fica depois das sugestões; sem ela, a última opção é a última sugestão.
    const last = this.allowCreate() ? this.suggestions().length : this.suggestions().length - 1;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.open.set(true);
      this.active.update((i) => Math.min(i + 1, last));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.update((i) => Math.max(i - 1, 0));
    } else if (event.key === 'Enter' && this.open()) {
      event.preventDefault();
      const index = this.active();
      if (index >= 0 && index < this.suggestions().length) {
        this.pick(this.suggestions()[index]);
      } else if (index === last && this.allowCreate()) {
        this.create(false);
      }
    } else if (event.key === 'Escape') {
      this.open.set(false);
    }
  }

  protected pick(item: CatalogItem): void {
    this.value.set(item);
    this.candidates.set([]);
    this.open.set(false);
    this.error.set(null);
  }

  protected create(confirmNew: boolean): void {
    const name = this.query().trim();
    if (!name) {
      return;
    }
    this.open.set(false);
    this.error.set(null);
    this.api
      .resolve(this.kind(), {
        name,
        confirmNew,
        breederId: this.breederId(),
        brandId: this.brandId(),
        category: this.category(),
      })
      .subscribe({
        next: (resolution) => {
          if (resolution.outcome === 'SIMILAR') {
            this.candidates.set(resolution.candidates);
          } else if (resolution.item) {
            this.pick(resolution.item);
          }
        },
        error: (error) => this.error.set(problemMessage(error)),
      });
  }

  protected clear(): void {
    this.value.set(null);
    this.query.set('');
    this.candidates.set([]);
  }

  protected closeSoon(): void {
    setTimeout(() => this.open.set(false), 150);
  }
}
