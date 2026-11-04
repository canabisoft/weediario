import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MyProfile } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { SeedbankShowcase } from './seedbank-showcase';

const ME: MyProfile = {
  id: 'u1', username: 'quintal', hasAvatar: false, hasRecoveryPhrase: true, role: 'GROWER', termsAccepted: true,
  currentTermsVersion: '1.0', profileHidden: false,
};

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
  function setup(member = true) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    if (member) {
      TestBed.inject(AuthService).me.set(ME);
    }
    const fixture = TestBed.createComponent(SeedbankShowcase);
    fixture.componentRef.setInput('breederId', 'b1');
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController) };
  }

  const cards = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('section[aria-labelledby="bank-strains"] li')).map((li) => li.textContent ?? '');

  it('shows one card per strain of the seedbank and, to members, the latest grows with its seeds', () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:capa');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
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
    // A capa é foto de diário da comunidade: vem pela API autenticada, nunca como <img> direto.
    http.expectOne('/p?size=THUMBNAIL').flush(new Blob(['x']));
    fixture.detectChanges();

    const texts = cards(fixture.nativeElement);
    expect(texts).toHaveLength(2);
    expect(texts[0]).toContain('Gelato 33');
    expect(texts[0]).toContain('4 cultivos');
    expect(texts[0]).toContain('4,5');
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('src')).toBe('blob:capa');
    expect(fixture.nativeElement.textContent).toContain('Ainda não há cultivos da comunidade');
    http.verify();
    vi.restoreAllMocks();
  });

  it('shows the strain cards to visitors but invites them to join to see the grows', () => {
    const { fixture, http } = setup(false);

    http.expectOne((req) => req.url === '/api/v1/catalog/breeders/b1/strains').flush({
      items: [strain('s1', 'Gelato 33', { diaries: 4 })], page: 0, size: 24, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();

    expect(cards(fixture.nativeElement)[0]).toContain('4 cultivos');
    expect(fixture.nativeElement.querySelector('app-members-only')).not.toBeNull();
    // Sem pedir o feed: visitante veria 401.
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
