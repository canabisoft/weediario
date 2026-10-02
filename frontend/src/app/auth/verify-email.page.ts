import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';

/** Link de confirmação do e-mail ({@code /confirmar-email?token=...}). Funciona sem estar logado, em qualquer aparelho. */
@Component({
  selector: 'app-verify-email-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Confirmar e-mail</h1>
      <div class="wd-card mt-6 grid gap-2" role="status">
        @switch (state()) {
          @case ('checking') {
            <p class="text-sm text-fg-muted">Confirmando…</p>
          }
          @case ('done') {
            <p class="font-semibold text-accent">E-mail confirmado!</p>
            <p class="text-sm text-fg-muted">Agora ele pode ser usado para redefinir a senha e recebe os avisos de segurança da conta.</p>
            <a [routerLink]="auth.isLoggedIn() ? '/perfil' : '/entrar'" class="text-sm underline">Continuar</a>
          }
          @case ('failed') {
            <p class="text-sm text-danger">{{ error() }}</p>
            <p class="text-sm text-fg-muted">Dá para pedir outro link no seu perfil, em "Segurança da conta".</p>
          }
        }
      </div>
    </section>
  `,
})
export class VerifyEmailPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly token = input<string | undefined>();

  protected readonly state = signal<'checking' | 'done' | 'failed'>('checking');
  protected readonly error = signal('');

  async ngOnInit(): Promise<void> {
    const token = this.token();
    if (!token) {
      this.error.set('Este link está incompleto.');
      this.state.set('failed');
      return;
    }
    try {
      await this.auth.verifyEmail(token);
      this.state.set('done');
      // O token sai da barra de endereço e do histórico.
      void this.router.navigate([], { queryParams: {}, replaceUrl: true });
    } catch (error) {
      this.error.set(problemMessage(error, 'Link inválido ou expirado.'));
      this.state.set('failed');
    }
  }
}
