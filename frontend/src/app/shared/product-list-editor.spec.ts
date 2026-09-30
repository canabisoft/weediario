import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogItem } from '../core/api/catalog-db.api';
import { ProductListEditor } from './product-list-editor';

describe('ProductListEditor', () => {
  function create() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(ProductListEditor);
    fixture.detectChanges();
    const editor = fixture.componentInstance as unknown as {
      product: { set(item: CatalogItem | null): void };
      brand: { set(item: CatalogItem | null): void; (): CatalogItem | null };
    };
    return { fixture, editor };
  }

  const item = (id: string, name: string) =>
    ({ id, name, parentName: 'BioBizz', category: 'NUTRIENT' }) as unknown as CatalogItem;

  it('adds the product as soon as it is picked, without an extra confirmation step', () => {
    const { fixture, editor } = create();

    editor.brand.set({ id: 'b1', name: 'BioBizz' } as unknown as CatalogItem);
    editor.product.set(item('p1', 'Bio-Grow'));
    fixture.detectChanges();

    expect(fixture.componentInstance.products()).toEqual([{ id: 'p1', name: 'Bio-Grow', brand: 'BioBizz', category: 'NUTRIENT' }]);
    // A marca continua escolhida para adicionar o próximo produto da mesma linha.
    expect(editor.brand()?.id).toBe('b1');
  });

  it('does not add the same product twice', () => {
    const { fixture, editor } = create();

    editor.product.set(item('p1', 'Bio-Grow'));
    fixture.detectChanges();
    editor.product.set(item('p1', 'Bio-Grow'));
    fixture.detectChanges();

    expect(fixture.componentInstance.products()).toHaveLength(1);
  });
});
