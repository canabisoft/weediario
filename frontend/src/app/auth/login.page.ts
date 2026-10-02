import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { ACCESS_KEY_PREFIX, AuthService } from '../core/auth/auth.service';
import { NewPassword } from './new-password';

/**
 * Login com apelido + senha. Conta criada na época da chave de acesso: quem cola a chave ({@code wd_sec_...}) no
 * campo de senha cria uma senha ali mesmo, e a chave deixa de valer.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, NewPassword],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Entrar</h1>
      <p class="mt-1 text-fg-muted">Apelido e senha. Sua sessão fica só neste aparelho.</p>

      <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="wd-label" for="login">Apelido</label>
          <input
            id="login"
            type="text"
            class="wd-input"
            formControlName="login"
            autocomplete="username"
            autocapitalize="none"
            spellcheck="false"
          />
        </div>
        <div>
          <label class="wd-label" for="password">Senha</label>
          <input
            id="password"
            type="password"
            class="wd-input"
            formControlName="password"
            autocomplete="current-password"
            autocapitalize="none"
            spellcheck="false"
          />
        </div>

        @if (usingAccessKey()) {
          <div class="grid gap-3 rounded-xl border border-line p-3">
            <p class="text-sm">
              Isso é a chave de acesso antiga. O weediário voltou a usar senha: crie uma agora e a chave deixa de valer.
            </p>
            <app-new-password id="from-key" label="Senha nova" (changed)="newPassword.set($event)" />
          </div>
        }

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }

        <button type="submit" class="wd-button" [disabled]="form.invalid || busy() || (usingAccessKey() && !newPassword())">
          {{ busy() ? 'Entrando…' : usingAccessKey() ? 'Criar senha e entrar' : 'Entrar' }}
        </button>
      </form>

      <p class="mt-4 text-center text-sm text-fg-muted">
        Esqueceu a senha? <a routerLink="/recuperar" class="font-semibold text-accent">Recuperar a conta</a>
      </p>
      <p class="mt-2 text-center text-sm text-fg-muted">
        Ainda não tem conta? <a routerLink="/cadastro" class="font-semibold text-accent">Criar conta</a>
      </p>
    </section>
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Página para onde voltar depois do login (vem do guard). */
  readonly voltar = input<string | undefined>();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    login: ['', Validators.required],
    password: ['', Validators.required],
  });
  private readonly typedPassword = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });
  protected readonly usingAccessKey = computed(() => this.typedPassword().trim().startsWith(ACCESS_KEY_PREFIX));
  protected readonly newPassword = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const login = this.form.controls.login.value.trim();
      const secret = this.form.controls.password.value;
      const newPassword = this.newPassword();
      if (this.usingAccessKey() && newPassword) {
        await this.auth.loginWithAccessKey(login, secret.trim(), newPassword);
      } else {
        await this.auth.login(login, secret);
      }
      this.form.controls.password.reset();
      // Conta criada antes da frase de recuperação: gera a frase antes de seguir.
      await this.router.navigateByUrl(this.auth.needsRecoveryPhrase() ? '/frase-de-recuperacao' : safeReturnUrl(this.voltar()));
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível entrar.'));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Só aceita caminhos internos, para o parâmetro "voltar" não virar um redirecionamento para outro site. */
export function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/diarios';
}
