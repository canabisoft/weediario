import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SeedbankShowcase } from './seedbank-showcase';

const strain = (id: string, name: string, extra: object = {}) => ({
  id,
  name,
  status: 'UNVERIFIED',
  source: 'COMMUNITY',
  diaries: 1,
  reviewCount: 0,
  ...extra,
});

describe('SeedbankShowcase', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const fixture = TestBed.createComponent(SeedbankShowcase);
    fixture.componentRef.setInput('breederId', 'b1');
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  const cards = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('section[aria-labelledby="bank-strains"] li')).map((li) => li.textContent ?? '');

  it('shows one card per strain of the seedbank and the latest public grows with its seeds', () => {
    const { fixture, http } = setup();

    http.expectOne((req) => req.url === '/api/v1/catalog/breeders/b1/strains' && req.params.get('page') === '0').flush({
      items: [strain('s1', 'Gelato 33', { diaries: 4, reviewCount: 2, overall: 4.5, coverThumbnailUrl: '/p?size=THUMBNAIL' }), strain('s2', 'Zkittlez')],
      page: 0,
      size: 24,
      totalItems: 2,
      totalPages: 1,
    });
    const feed = http.expectOne((req) => req.url === '/api/v1/feed/diaries');
    expect(feed.request.params.get('breederId')).toBe('b1');
    expect(feed.request.params.get('sort')).toBe('NEWEST');
    feed.flush({ items: [], page: 0, size: 6, totalItems: 0, totalPages: 0 });
    fixture.detectChanges();

    const texts = cards(fixture.nativeElement);
    expect(texts).toHaveLength(2);
    expect(texts[0]).toContain('Gelato 33');
    expect(texts[0]).toContain('4 cultivos');
    expect(texts[0]).toContain('4,5');
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('src')).toBe('/p?size=THUMBNAIL');
    expect(fixture.nativeElement.textContent).toContain('Ainda não há cultivos públicos');
    http.verify();
  });

  it('appends the next page when asking for more seeds', () => {
    const { fixture, http } = setup();
    http.expectOne((req) => req.url === '/api/v1/feed/diaries').flush({ items: [], page: 0, size: 6, totalItems: 0, totalPages: 0 });
    http.expectOne((req) => req.params.get('page') === '0').flush({
      items: [strain('s1', 'Primeira')], page: 0, size: 24, totalItems: 2, totalPages: 2,
    });
    fixture.detectChanges();

    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.includes('Ver mais'))!
      .click();
    fixture.detectChanges();
    http.expectOne((req) => req.params.get('page') === '1').flush({
      items: [strain('s2', 'Segunda')], page: 1, size: 24, totalItems: 2, totalPages: 2,
    });
    fixture.detectChanges();

    const names = Array.from(fixture.nativeElement.querySelectorAll('section[aria-labelledby="bank-strains"] li p.font-semibold'))
      .map((p) => (p as HTMLElement).textContent?.trim());
    expect(names).toEqual(['Primeira', 'Segunda']);
    expect(fixture.nativeElement.textContent).not.toContain('Ver mais sementes');
  });
});
