import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { NewPassword } from './new-password';

/**
 * Link de redefinição recebido por e-mail ({@code /redefinir-senha?token=...}). O token vale 30 minutos e uma vez só;
 * usar o link encerra as outras sessões da conta.
 */
@Component({
  selector: 'app-reset-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NewPassword],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Criar senha nova</h1>

      @if (token()) {
        <form class="wd-card mt-6 grid gap-4" (submit)="$event.preventDefault(); submit()">
          <app-new-password id="reset" label="Senha nova" (changed)="password.set($event)" />
          @if (error()) {
            <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
          }
          <button type="submit" class="wd-button" [disabled]="!password() || busy()">
            {{ busy() ? 'Salvando…' : 'Salvar e entrar' }}
          </button>
        </form>
      } @else {
        <p class="wd-card mt-6 text-sm">
          Este link está incompleto. Peça outro em <a routerLink="/recuperar" class="font-semibold text-accent">Recuperar conta</a>.
        </p>
      }
    </section>
  `,
})
export class ResetPasswordPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly token = input<string | undefined>();

  protected readonly password = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    const token = this.token();
    const password = this.password();
    if (!token || !password) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.confirmPasswordReset(token, password);
      // O token não fica no histórico do navegador depois de usado.
      await this.router.navigateByUrl('/perfil', { replaceUrl: true });
    } catch (error) {
      this.error.set(problemMessage(error, 'Link inválido ou expirado. Peça outro.'));
    } finally {
      this.busy.set(false);
    }
  }
}
