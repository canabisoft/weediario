import { ChangeDetectionStrategy, Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { ProductSnapshot } from '../../core/api/api.models';
import { DiariesApi } from '../../core/api/diaries.api';
import { problemMessage } from '../../core/api/problem';
import { Icon } from '../../shared/icon';
import { categoryLabel } from '../../shared/labels';
import { ProductListEditor } from '../../shared/product-list-editor';

/**
 * Produtos usados no cultivo. O autor pode completar ou corrigir a qualquer momento, inclusive em cultivos
 * abertos sem setup: é o que faz o nutriente aparecer (e contar) em "Produtos".
 */
@Component({
  selector: 'app-diary-products',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, ProductListEditor],
  template: `
    <div class="flex items-center justify-between gap-2">
      <h2 class="font-semibold">Produtos usados</h2>
      @if (isOwner() && !editing()) {
        <button type="button" class="inline-flex items-center gap-1 text-sm text-accent" (click)="startEdit()">
          <app-icon name="edit" class="size-4" /> {{ saved().length ? 'Editar' : 'Adicionar' }}
        </button>
      }
    </div>

    @if (editing()) {
      <app-product-list-editor class="mt-3" [(products)]="draft" />
      @if (error()) {
        <p role="alert" class="mt-2 text-sm text-danger">{{ error() }}</p>
      }
      <div class="mt-3 flex gap-2">
        <button type="button" class="wd-button" [disabled]="busy()" (click)="save()">Salvar produtos</button>
        <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="editing.set(false)">Cancelar</button>
      </div>
    } @else if (saved().length) {
      <ul class="mt-2 flex flex-wrap gap-2">
        @for (product of saved(); track product.id) {
          <li class="rounded-full bg-surface-raised px-3 py-1 text-sm">
            <span class="text-fg-muted">{{ categoryLabel(product.category) }}:</span> {{ product.brand }} · {{ product.name }}
          </li>
        }
      </ul>
    } @else {
      <p class="mt-2 text-sm text-fg-muted">
        {{ isOwner() ? 'Informe nutrientes, substrato e aditivos para o cultivo contar no ranking de Produtos.' : 'Nenhum produto informado.' }}
      </p>
    }
  `,
})
export class DiaryProducts {
  private readonly api = inject(DiariesApi);

  readonly diaryId = input.required<string>();
  readonly products = input<readonly ProductSnapshot[]>([]);
  readonly isOwner = input(false);
  readonly changed = output<void>();

  protected readonly categoryLabel = categoryLabel;
  protected readonly saved = linkedSignal(() => [...this.products()]);
  protected readonly draft = signal<ProductSnapshot[]>([]);
  protected readonly editing = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected startEdit(): void {
    this.draft.set([...this.saved()]);
    this.error.set(null);
    this.editing.set(true);
  }

  protected save(): void {
    this.busy.set(true);
    this.api.changeProducts(this.diaryId(), this.draft().map((product) => product.id)).subscribe({
      next: (products) => {
        this.saved.set(products);
        this.busy.set(false);
        this.editing.set(false);
        this.changed.emit();
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
