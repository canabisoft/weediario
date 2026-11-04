import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { AccountDataPanel } from '../profile/account-data';

const day = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'long', year: 'numeric' });

/**
 * Tudo o que a conta banida vê (Termos de Uso, cláusula 9): por 30 dias ela ainda entra, só para baixar os próprios
 * dados. A API recusa qualquer outra coisa, então esta tela é a única do app para ela.
 */
@Component({
  selector: 'app-banned-export',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AccountDataPanel],
  template: `
    <section class="mx-auto grid max-w-xl gap-4">
      <h1 class="text-2xl font-bold">Esta conta foi banida</h1>
      <p class="text-fg-muted">
        A moderação encerrou a conta <strong class="text-fg">{{ username() }}</strong> por violação
        dos Termos de Uso. Até <strong class="text-fg">{{ until() }}</strong> você ainda pode entrar
        para baixar os seus dados; depois disso, o acesso termina.
      </p>
      <p class="text-sm text-fg-muted">
        Se você discorda da decisão, escreva para bruno.dev@weediario.com contando a sua versão.
      </p>
      <app-account-data [username]="username()" [allowDelete]="false" />
      <button
        type="button"
        class="wd-button justify-self-start border border-line bg-transparent px-5 text-fg"
        (click)="logout()"
      >
        Sair
      </button>
    </section>
  `,
})
export class BannedExport {
  private readonly auth = inject(AuthService);

  protected readonly username = computed(() => this.auth.me()?.username ?? '');
  protected readonly until = computed(() => {
    const until = this.auth.me()?.exportOnlyUntil;
    return until ? day.format(new Date(until)) : '';
  });

  protected logout(): void {
    void this.auth.logout();
  }
}
