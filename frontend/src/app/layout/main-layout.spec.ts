import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    document.documentElement.setAttribute('data-theme', 'light');
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  it('renders the shell with the main navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const nav = (fixture.nativeElement as HTMLElement).querySelector('nav[aria-label="Navegação principal"]');

    expect(nav?.textContent).toContain('Ferramentas');
    expect(nav?.textContent).toContain('Ajustes');
  });

  it('switches to night mode with one tap from the header', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector('header button') as HTMLButtonElement;

    button.click();
    await fixture.whenStable();

    expect(document.documentElement.getAttribute('data-theme')).toBe('night');
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.textContent).toContain('Sair do noturno');
  });
});
