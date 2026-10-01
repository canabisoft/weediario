import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReviewForm } from './review-form';

describe('ReviewForm', () => {
  function setup(products: object[]) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const fixture = TestBed.createComponent(ReviewForm);
    fixture.componentRef.setInput('diaryId', 'd1');
    fixture.componentRef.setInput('strain', 'Gelato 33');
    fixture.componentRef.setInput('products', products);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) };
  }

  /** Clica a estrela {@code stars} do grupo de rádio {@code name}. */
  function rate(root: HTMLElement, name: string, stars: number) {
    (root.querySelector(`input[name="${name}"][value="${stars}"]`) as HTMLInputElement).click();
  }

  async function fillStrain(fixture: ReturnType<typeof setup>['fixture'], root: HTMLElement) {
    // O ngModel dentro do form só se registra depois da primeira rodada de detecção.
    await fixture.whenStable();
    for (const name of ['vigor', 'pest', 'ease', 'yield', 'terpenes']) {
      rate(root, name, 4);
    }
    const report = root.querySelector('textarea') as HTMLTextAreaElement;
    report.value = 'Flora de 9 semanas, aroma de limão e rendimento bom.';
    report.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('rates only the products the grower chose to rate', async () => {
    const { fixture, root, http } = setup([
      { id: 'p1', name: 'Bio-Bloom', brand: 'BioBizz', category: 'NUTRIENT' },
      { id: 'p2', name: 'Light Mix', brand: 'BioBizz', category: 'SUBSTRATE' },
    ]);
    expect(root.textContent).toContain('Produtos usados');
    expect(root.textContent).toContain('BioBizz Bio-Bloom');

    await fillStrain(fixture, root);
    rate(root, 'product-p2', 2);
    fixture.detectChanges();
    const submit = root.querySelector('button[type="submit"]') as HTMLButtonElement;
    submit.click();

    const request = http.expectOne({ method: 'POST', url: '/api/v1/diaries/d1/review' });
    expect(request.request.body.products).toEqual([{ productId: 'p2', rating: 2 }]);
    expect(request.request.body.vigor).toBe(4);
    http.verify();
  });

  it('hides the product section and sends no products when the grow used none', async () => {
    const { fixture, root, http } = setup([]);
    expect(root.textContent).not.toContain('Produtos usados');

    await fillStrain(fixture, root);
    const submit = root.querySelector('button[type="submit"]') as HTMLButtonElement;
    submit.click();

    const request = http.expectOne({ method: 'POST', url: '/api/v1/diaries/d1/review' });
    expect('products' in request.request.body).toBe(false);
  });
});
