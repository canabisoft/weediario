import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ManifestoPage, REPOSITORY_URL } from './manifesto.page';

describe('ManifestoPage', () => {
  it('states the privacy principles and invites contributions to the open source code', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(ManifestoPage);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const text = root.textContent!.replace(/\s+/g, ' ');

    expect(text).toContain('Nunca pedimos CPF, nome completo, RG, endereço ou telefone');
    expect(text).toContain('Não guardamos logs de acesso');
    expect(text).toContain('Não indicamos dose');
    expect(text).toContain('qualquer um pode contribuir');

    const github = root.querySelector<HTMLAnchorElement>(`a[href="${REPOSITORY_URL}"]`)!;
    expect(github.rel).toContain('noopener');
    expect(github.target).toBe('_blank');
  });
});
