import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ManifestoPage } from './manifesto.page';

describe('ManifestoPage', () => {
  it('states the privacy principles and links to the terms', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(ManifestoPage);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const text = root.textContent!.replace(/\s+/g, ' ');

    expect(text).toContain('Nunca pedimos CPF, nome completo, RG, endereço ou telefone');
    expect(text).toContain('só ligam se você aceitar os cookies');
    expect(text).toContain('Não indicamos dose');
    expect(text).not.toContain('open source');
    expect(root.querySelector('a[href="/termos"]')).not.toBeNull();
  });
});
