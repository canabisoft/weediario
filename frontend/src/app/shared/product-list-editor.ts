import { ChangeDetectionStrategy, Component, effect, model, signal } from '@angular/core';
import { ProductSnapshot } from '../core/api/api.models';
import { CatalogItem, ProductCategory } from '../core/api/catalog-db.api';
import { CatalogPicker } from './catalog-picker';
import { Icon } from './icon';
import { CATEGORY_LABEL, categoryLabel, options } from './labels';

/**
 * Lista de produtos do catálogo (fertilizantes, substratos...) escolhidos por categoria → marca → produto.
 * <p>
 * Marca e produto seguem a mesma lógica das strains: o autocomplete sugere o que a comunidade já cadastrou e,
 * se não existir, "Adicionar" cria (com checagem de duplicata). O produto entra na lista assim que é escolhido:
 * antes havia um botão extra e quem salvava sem clicar nele perdia o produto sem perceber.
 */
@Component({
  selector: 'app-product-list-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CatalogPicker, Icon],
  template: `
    @if (products().length) {
      <ul class="flex flex-wrap gap-2" aria-label="Produtos escolhidos">
        @for (item of products(); track item.id) {
          <li class="flex items-center gap-1 rounded-full bg-surface-raised py-1 pl-3 pr-1 text-sm">
            <span><span class="text-fg-muted">{{ categoryLabel(item.category) }}:</span> {{ item.brand }} · {{ item.name }}</span>
            <button type="button" class="grid size-7 place-items-center rounded-full text-fg-muted" [attr.aria-label]="'Remover ' + item.name" (click)="remove(item.id)">
              <app-icon name="close" class="size-3.5" />
            </button>
          </li>
        }
      </ul>
    } @else {
      <p class="text-sm text-fg-muted">Nenhum produto ainda.</p>
    }
    <div>
      <label class="wd-label" [for]="categoryId">Categoria</label>
      <select [id]="categoryId" class="wd-input" (change)="category.set($any($event.target).value)">
        @for (o of categoryOptions; track o.value) {
          <option [value]="o.value" [selected]="category() === o.value">{{ o.label }}</option>
        }
      </select>
    </div>
    <app-catalog-picker kind="brands" label="Marca" placeholder="Ex.: BioBizz" [(value)]="brand" />
    @if (brand(); as selectedBrand) {
      <app-catalog-picker
        kind="products"
        label="Produto"
        placeholder="Ex.: Bio-Grow"
        [brandId]="selectedBrand.id"
        [category]="category()"
        [(value)]="product"
      />
    }
    <p class="text-xs text-fg-muted">
      Não achou a marca ou o produto? Digite o nome e toque em "Adicionar": ele entra no catálogo da comunidade e
      passa a contar no ranking de Produtos.
    </p>
  `,
  host: { class: 'grid gap-3' },
})
export class ProductListEditor {
  private static nextId = 0;

  readonly products = model<ProductSnapshot[]>([]);

  protected readonly categoryId = `product-category-${ProductListEditor.nextId++}`;
  protected readonly categoryOptions = options(CATEGORY_LABEL);
  protected readonly categoryLabel = categoryLabel;
  protected readonly category = signal<ProductCategory>('NUTRIENT');
  protected readonly brand = signal<CatalogItem | null>(null);
  protected readonly product = signal<CatalogItem | null>(null);

  constructor() {
    effect(() => {
      const chosen = this.product();
      if (!chosen) {
        return;
      }
      if (!this.products().some((item) => item.id === chosen.id)) {
        this.products.set([
          ...this.products(),
          { id: chosen.id, name: chosen.name, brand: chosen.parentName ?? this.brand()?.name ?? '', category: chosen.category ?? this.category() },
        ]);
      }
      // Mantém a marca: é comum usar vários produtos da mesma linha (ex.: Grow, Bloom, Micro).
      this.product.set(null);
    });
  }

  protected remove(id: string): void {
    this.products.set(this.products().filter((item) => item.id !== id));
  }
}
