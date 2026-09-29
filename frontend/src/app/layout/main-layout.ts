import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DOCUMENT, DestroyRef, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConnectivityService } from '../core/network/connectivity.service';
import { AuthService } from '../core/auth/auth.service';
import { ThemeService } from '../core/theme/theme.service';
import { Avatar } from '../shared/avatar';
import { Icon, IconName } from '../shared/icon';

interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: IconName;
  readonly exact?: boolean;
}

/** Largura a partir da qual o menu lateral fica fixo na tela (breakpoint lg do Tailwind). */
const DESKTOP_QUERY = '(min-width: 1024px)';

/**
 * Casca da aplicação: menu lateral (fixo no desktop, gaveta no celular), cabeçalho com busca e ação
 * rápida, e a área onde as páginas são renderizadas.
 */
@Component({
  selector: 'app-main-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgTemplateOutlet, Icon, Avatar],
  templateUrl: './main-layout.html',
  host: { '(document:keydown.escape)': 'closeMenu()' },
})
export class MainLayout {
  protected readonly theme = inject(ThemeService);
  protected readonly auth = inject(AuthService);
  protected readonly connectivity = inject(ConnectivityService);
  private readonly router = inject(Router);
  /** Sem suporte a matchMedia (navegadores antigos), assume celular: a gaveta sempre funciona. */
  private readonly desktopMedia = desktopMediaQuery(inject(DOCUMENT).defaultView);

  protected readonly menuOpen = signal(false);
  protected readonly isDesktop = signal(this.desktopMedia?.matches ?? false);

  protected readonly primaryNav: readonly NavItem[] = [
    { path: '/', label: 'Home', icon: 'home', exact: true },
    { path: '/diarios', label: 'Diários', icon: 'diaries' },
    { path: '/strains', label: 'Strains', icon: 'leaf' },
    { path: '/growers', label: 'Ranking de growers', icon: 'trophy' },
    { path: '/equipamentos', label: 'Equipamentos', icon: 'bulb' },
  ];

  protected readonly secondaryNav: readonly NavItem[] = [
    { path: '/ferramentas', label: 'Ferramentas', icon: 'tools' },
    { path: '/ajustes', label: 'Ajustes', icon: 'settings' },
  ];

  constructor() {
    const media = this.desktopMedia;
    const onChange = (event: MediaQueryListEvent) => {
      this.isDesktop.set(event.matches);
      if (event.matches) {
        this.menuOpen.set(false);
      }
    };
    media?.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => media?.removeEventListener('change', onChange));

    // Navegar pelo menu da gaveta fecha a gaveta.
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected openMenu(): void {
    this.menuOpen.set(true);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** Por enquanto a busca global procura growers; diários e strains entram quando essas páginas existirem. */
  protected search(event: Event): void {
    event.preventDefault();
    const query = String(new FormData(event.target as HTMLFormElement).get('q') ?? '').trim();
    this.router.navigate(['/growers'], { queryParams: query ? { q: query } : {} });
  }
}

function desktopMediaQuery(window: Window | null): MediaQueryList | null {
  return window && typeof window.matchMedia === 'function' ? window.matchMedia(DESKTOP_QUERY) : null;
}
