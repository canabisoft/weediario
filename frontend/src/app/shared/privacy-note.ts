import { ChangeDetectionStrategy, Component, DOCUMENT, DestroyRef, inject, input, signal } from '@angular/core';
import { Icon } from './icon';

/**
 * Mensagens de privacidade exibidas em rodízio. Alternam entre os dois compromissos da plataforma:
 * não guardar logs e limpar os metadados das fotos antes de publicar.
 */
export const PRIVACY_MESSAGES: readonly string[] = [
  'Aqui não tem log: o que rola na sua tenda fica entre você e a comunidade.',
  'Foto limpa: a gente tira GPS, modelo do celular e data antes de postar.',
  'Não guardamos logs. Seu corre é só seu.',
  'Antes de ir pro ar, cada foto passa por uma faxina nos metadados.',
  'Sem logs, sem rastro das suas atividades. Cultiva em paz.',
  'Postou foto? Relaxa, os metadados já ficaram pra trás.',
];

const ROTATION_MS = 7000;

@Component({
  selector: 'app-privacy-note',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block rounded-2xl border border-accent/30 bg-accent/10 p-4' },
  template: `
    <p class="flex items-center gap-2 text-sm font-semibold text-accent">
      <app-icon name="lock" class="size-4" [strokeWidth]="2.2" />
      {{ title() }}
    </p>
    <!-- Sem aria-live: o rodízio não deve interromper leitores de tela a cada troca. -->
    <p class="mt-1 min-h-[3lh] text-xs leading-relaxed text-fg-muted transition-opacity duration-300" [class.opacity-0]="fading()">
      {{ messages[index()] }}
    </p>
  `,
})
export class PrivacyNote {
  readonly title = input('Sua privacidade vem primeiro');

  protected readonly messages = PRIVACY_MESSAGES;
  /** Começa numa mensagem aleatória para variar entre telas e visitas. */
  protected readonly index = signal(Math.floor(Math.random() * PRIVACY_MESSAGES.length));
  protected readonly fading = signal(false);

  constructor() {
    const window = inject(DOCUMENT).defaultView;
    const reducedMotion = typeof window?.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!window || reducedMotion) {
      return;
    }
    let fadeTimer: ReturnType<typeof setTimeout> | undefined;
    const timer = window.setInterval(() => {
      this.fading.set(true);
      fadeTimer = setTimeout(() => {
        this.index.update((current) => (current + 1) % PRIVACY_MESSAGES.length);
        this.fading.set(false);
      }, 300);
    }, ROTATION_MS);
    inject(DestroyRef).onDestroy(() => {
      window.clearInterval(timer);
      clearTimeout(fadeTimer);
    });
  }
}
