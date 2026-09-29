import { DOCUMENT, Injectable, computed, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark' | 'night';

export const THEMES: readonly { id: Theme; label: string; hint: string }[] = [
  { id: 'light', label: 'Claro', hint: 'Para usar de dia' },
  { id: 'dark', label: 'Escuro', hint: 'Confortável à noite' },
  { id: 'night', label: 'Noturno de cultivo', hint: 'Verde escuro, sem luz branca: para o período de escuro da tenda' },
];

const STORAGE_KEY = 'wd-theme';

/** Cor da barra do navegador/sistema em cada tema (a do modo noturno é preta, para não emitir luz). */
const BROWSER_BAR_COLOR: Record<Theme, string> = {
  light: '#f5f7f4',
  dark: '#0f1411',
  night: '#000000',
};

/**
 * Guarda a escolha só neste aparelho (localStorage). É uma preferência de exibição, não um dado
 * pessoal, então não vai para o servidor.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  readonly theme = signal<Theme>(this.initialTheme());
  readonly isNight = computed(() => this.theme() === 'night');

  /** Tema para onde o atalho volta ao sair do modo noturno. */
  private dayTheme: Exclude<Theme, 'night'> = this.theme() === 'light' ? 'light' : 'dark';

  constructor() {
    effect(() => this.apply(this.theme()));
  }

  select(theme: Theme): void {
    if (theme !== 'night') {
      this.dayTheme = theme;
    }
    this.theme.set(theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Armazenamento indisponível (modo privado): o tema vale só nesta sessão.
    }
  }

  /** Atalho de um toque para entrar/sair do modo noturno, disponível em qualquer tela. */
  toggleNight(): void {
    this.select(this.isNight() ? this.dayTheme : 'night');
  }

  private initialTheme(): Theme {
    // theme-init.js já aplicou o tema salvo antes do Angular carregar; aqui só lemos o resultado.
    const applied = this.document.documentElement.getAttribute('data-theme');
    return isTheme(applied) ? applied : 'light';
  }

  private apply(theme: Theme): void {
    this.document.documentElement.setAttribute('data-theme', theme);
    this.document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BROWSER_BAR_COLOR[theme]);
  }
}

function isTheme(value: string | null): value is Theme {
  return value === 'light' || value === 'dark' || value === 'night';
}
