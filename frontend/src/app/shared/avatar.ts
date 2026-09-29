import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { avatarUrl } from '../core/api/social.api';
import { initialOf } from '../growers/grower-ranking';

/**
 * Foto de perfil com fallback para a inicial do apelido (quando não há foto ou ela falha ao carregar).
 */
@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative inline-grid shrink-0 place-items-center overflow-hidden rounded-full bg-accent/15 font-semibold text-accent ring-1 ring-accent/30' },
  template: `
    <span aria-hidden="true">{{ initial() }}</span>
    @if (hasAvatar() && !failed()) {
      <img
        [src]="src()"
        alt=""
        class="absolute inset-0 size-full object-cover"
        loading="lazy"
        referrerpolicy="no-referrer"
        (error)="failed.set(true)"
      />
    }
  `,
})
export class Avatar {
  readonly username = input.required<string>();
  readonly hasAvatar = input(false);
  /** Muda quando o usuário troca a foto, para furar o cache do navegador. */
  readonly version = input(0);

  protected readonly failed = signal(false);
  protected readonly initial = computed(() => initialOf(this.username()));
  protected readonly src = computed(() => `${avatarUrl(this.username())}?v=${this.version()}`);
}
