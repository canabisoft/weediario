import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from './icon';

/**
 * O weediário é uma comunidade fechada: diários, perfis e relatos de quem cultiva só aparecem para quem tem conta.
 * Nas páginas abertas (catálogo, início), este card ocupa o lugar desse conteúdo para quem não entrou.
 */
@Component({
  selector: 'app-members-only',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="wd-card grid gap-3 sm:flex sm:items-center">
      <span class="grid size-10 shrink-0 place-items-center rounded-xl bg-accent/15 text-accent">
        <app-icon name="lock" class="size-5" />
      </span>
      <p class="text-sm text-fg-muted sm:flex-1">{{ text() }}</p>
      <div class="flex flex-wrap gap-2">
        <a routerLink="/cadastro" class="wd-button">Criar conta</a>
        <a
          routerLink="/entrar"
          [queryParams]="{ voltar: returnTo() }"
          class="wd-button bg-surface-raised text-fg"
          >Entrar</a
        >
      </div>
    </div>
  `,
})
export class MembersOnly {
  readonly text = input(
    'Diários, perfis e relatos de cultivo são só para quem tem conta. É de graça e não pede nome real.',
  );
  readonly returnTo = input<string | undefined>(undefined);
}
