import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Entrar</h1>
      <p class="mt-1 text-fg-muted">Bem-vindo de volta. Sua sessão fica só neste aparelho.</p>

      <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="wd-label" for="login">E-mail ou apelido</label>
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
          />
        </div>

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }

        <button type="submit" class="wd-button" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Entrando…' : 'Entrar' }}
        </button>
      </form>

      <p class="mt-4 text-center text-sm text-fg-muted">
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
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { login, password } = this.form.getRawValue();
      await this.auth.login(login, password);
      await this.router.navigateByUrl(safeReturnUrl(this.voltar()));
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
