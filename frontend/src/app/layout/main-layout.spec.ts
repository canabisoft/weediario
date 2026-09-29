import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { of } from 'rxjs';
import { routes } from '../app.routes';
import { GROWER_RANKING_SOURCE } from '../growers/grower-ranking.source';
import { MainLayout } from './main-layout';

describe('MainLayout', () => {
  beforeEach(async () => {
    document.documentElement.setAttribute('data-theme', 'dark');
    await TestBed.configureTestingModule({
      imports: [MainLayout],
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: GROWER_RANKING_SOURCE, useValue: { load: () => of({ entries: [], total: 0 }) } },
      ],
    }).compileComponents();
  });

  async function render() {
    const fixture = TestBed.createComponent(MainLayout);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('renders the sidebar navigation and the privacy card', async () => {
    const { root } = await render();
    const nav = root.querySelector('nav[aria-label="Navegação principal"]')!;

    for (const label of ['Home', 'Diários', 'Strains', 'Equipamentos', 'Ranking de growers', 'Ferramentas']) {
      expect(nav.textContent).toContain(label);
    }
    expect(root.querySelector('aside')!.textContent).toContain('Sem logs de IP ou localização');
  });

  it('has a highlighted quick action to start a diary', async () => {
    const { root } = await render();
    const action = root.querySelector('header a[href="/diarios/novo"]');

    expect(action?.getAttribute('aria-label')).toBe('Iniciar diário');
    expect(action?.classList).toContain('wd-button');
  });

  it('opens the mobile drawer and closes it with Escape', async () => {
    const { fixture, root } = await render();
    const toggle = root.querySelector('button[aria-controls="menu-lateral"]') as HTMLButtonElement;

    toggle.click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(root.querySelector('aside')!.classList).toContain('translate-x-0');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('sends the global search to the growers ranking', async () => {
    const { fixture, root } = await render();
    const form = root.querySelector('header form[role="search"]') as HTMLFormElement;
    (form.querySelector('input[name="q"]') as HTMLInputElement).value = '  kaya ';

    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/growers?q=kaya');
  });

  it('offers login when nobody is signed in', async () => {
    const { root } = await render();

    expect(root.querySelector('header a[href="/entrar"]')).not.toBeNull();
  });

  it('toggles night mode with one tap from the header', async () => {
    const { fixture, root } = await render();
    const button = root.querySelector('header button[aria-pressed]') as HTMLButtonElement;

    button.click();
    await fixture.whenStable();

    expect(document.documentElement.getAttribute('data-theme')).toBe('night');
    expect(button.getAttribute('aria-pressed')).toBe('true');
  });
});
