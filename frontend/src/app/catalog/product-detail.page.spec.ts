import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ProductDetailPage } from './product-detail.page';

describe('ProductDetailPage', () => {
  it('shows the average rating and links only to public diaries', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const fixture = TestBed.createComponent(ProductDetailPage);
    fixture.componentRef.setInput('id', 'p1');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/v1/catalog/products/p1').flush({
      id: 'p1', name: 'Bio-Bloom', brandId: 'b1', brand: 'BioBizz', category: 'NUTRIENT', status: 'VERIFIED',
      source: 'DATASET', diaries: 7, recipes: 2, reviewCount: 2, rating: 4.5,
    });
    http.expectOne((req) => req.url === '/api/v1/catalog/products/p1/reviews').flush({
      items: [
        { id: 'r1', strainId: 's1', strain: 'Gelato 33', breeder: 'Barneys', rating: 5, diaryId: 'd1', createdAt: '2026-09-20T10:00:00Z' },
        { id: 'r2', strainId: 's2', strain: 'Zkittlez', rating: 4, createdAt: '2026-09-10T10:00:00Z' },
      ],
      page: 0, size: 20, totalItems: 2, totalPages: 1,
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h1')?.textContent).toContain('Bio-Bloom');
    expect(root.textContent).toContain('BioBizz · Nutriente');
    expect(root.textContent).toContain('4,5');
    expect(root.textContent).toContain('Gelato 33');
    const diaryLinks = Array.from(root.querySelectorAll('a')).filter((a) => a.textContent?.includes('Ver o diário'));
    expect(diaryLinks).toHaveLength(1);
    expect(diaryLinks[0].getAttribute('href')).toBe('/diarios/d1');
    http.verify();
  });
});
