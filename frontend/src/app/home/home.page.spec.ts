import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MyProfile } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { HomePage } from './home.page';

const ME: MyProfile = {
  id: 'u1',
  username: 'quintal',
  hasAvatar: false,
  hasRecoveryPhrase: true,
  role: 'GROWER',
  termsAccepted: true,
  currentTermsVersion: '1.0',
  profileHidden: false,
};

describe('HomePage', () => {
  function render(member: boolean) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    if (member) {
      TestBed.inject(AuthService).me.set(ME);
    }
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    return {
      root: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
    };
  }

  it('explains what weediário is to visitors without calling the API', () => {
    const { root, http } = render(false);

    expect(root.querySelector('h1')?.textContent).toContain('O seu cultivo é assunto seu.');
    expect(root.textContent).toContain('O que a gente nunca pede, nem guarda.');
    expect(root.querySelector('a[href="/cadastro"]')).not.toBeNull();
    // Sem menu lateral para visitante: as páginas abertas ficam linkadas na própria landing.
    for (const path of [
      '/strains',
      '/dados-abertos',
      '/breeders/programa',
      '/ferramentas',
      '/manifesto',
      '/termos',
    ]) {
      expect(root.querySelector(`a[href="${path}"]`)).not.toBeNull();
    }
    const photo = root.querySelector('img');
    expect(photo?.getAttribute('src')).toBe('landing/flor-460.webp');
    expect(photo?.getAttribute('alt')).toContain('cannabis');
    // Comunidade fechada: o feed responderia 401, então nem é pedido.
    http.verify();
  });

  it('keeps the member home with the latest diaries', () => {
    const { root, http } = render(true);

    expect(root.querySelector('h1')?.textContent).toContain('Seu diário de cultivo');
    http
      .expectOne((req) => req.url === '/api/v1/feed/diaries')
      .flush({ items: [], page: 0, size: 6, totalItems: 0, totalPages: 0 });
    http.verify();
  });
});
