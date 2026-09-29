import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página provisória para seções do menu cuja tela ainda não foi construída.
 * Título e descrição vêm do {@code data} da rota.
 */
@Component({
  selector: 'app-coming-soon-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="wd-card text-center">
      <p class="text-sm font-semibold uppercase tracking-wider text-accent">Em breve</p>
      <h1 class="mt-1 text-2xl font-bold">{{ heading() }}</h1>
      <p class="mx-auto mt-2 max-w-md text-fg-muted">{{ description() }}</p>
      <a routerLink="/ferramentas" class="wd-button mt-5">Usar as ferramentas enquanto isso</a>
    </section>
  `,
})
export class ComingSoonPage {
  readonly heading = input('Em construção');
  readonly description = input('Esta seção chega nas próximas versões.');
}
