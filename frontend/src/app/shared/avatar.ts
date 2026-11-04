import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { avatarUrl } from '../core/api/social.api';
import { initialOf } from '../growers/grower-ranking';
import { PhotoSource } from './photo-source';

/**
 * Foto de perfil com fallback para a inicial do apelido (quando não há foto ou ela falha ao carregar).
 * <p>
 * Comunidade fechada: a foto exige login, como as dos diários, e chega pela API autenticada como blob. Assim ela não
 * aparece para quem não tem conta nem serve para descobrir se um apelido existe.
 */
@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class:
      'relative inline-grid shrink-0 place-items-center overflow-hidden rounded-full bg-accent/15 font-semibold text-accent ring-1 ring-accent/30',
  },
  template: `
    <span aria-hidden="true">{{ initial() }}</span>
    @if (src(); as url) {
      <img
        [src]="url"
        alt=""
        class="absolute inset-0 size-full object-cover"
        decoding="async"
        (error)="src.set(null)"
      />
    }
  `,
})
export class Avatar {
  private readonly source = inject(PhotoSource);

  readonly username = input.required<string>();
  readonly hasAvatar = input(false);
  /** Muda quando o usuário troca a foto, para furar o cache do navegador. */
  readonly version = input(0);

  protected readonly src = signal<string | null>(null);
  protected readonly initial = computed(() => initialOf(this.username()));

  constructor() {
    effect((onCleanup) => {
      if (!this.hasAvatar()) {
        return;
      }
      const subscription = this.source
        .resolve(`${avatarUrl(this.username())}?v=${this.version()}`)
        .subscribe({ next: (src) => this.src.set(src), error: () => this.src.set(null) });
      onCleanup(() => {
        subscription.unsubscribe();
        this.src.set(null);
      });
    });
  }
}
