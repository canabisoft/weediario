import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { ACCESS_KEY_PREFIX, AuthService } from '../core/auth/auth.service';
import { AccessKeyReveal } from './access-key-reveal';

/**
 * Login com apelido + chave de acesso. Conta antiga (de antes da chave) entra uma última vez com a senha: ela é
 * trocada por uma chave, mostrada aqui.
 */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, AccessKeyReveal],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Entrar</h1>

      @if (newKey(); as key) {
        <section class="wd-card mt-6 grid gap-4">
          <div>
            <h2 class="text-lg font-semibold">Sua conta agora entra com chave de acesso</h2>
            <p class="text-sm text-fg-muted">A senha antiga foi apagada e não funciona mais.</p>
          </div>
          <app-access-key-reveal [username]="form.controls.login.value" [accessKey]="key" (done)="continue()" />
        </section>
      } @else {
        <p class="mt-1 text-fg-muted">Apelido e chave de acesso. Sua sessão fica só neste aparelho.</p>

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
            <label class="wd-label" for="access-key">Chave de acesso</label>
            <input
              id="access-key"
              type="password"
              class="wd-input font-mono"
              formControlName="accessKey"
              autocomplete="current-password"
              autocapitalize="none"
              spellcheck="false"
              placeholder="wd_sec_…"
            />
            <p class="mt-1 text-xs text-fg-muted">Conta criada antes da chave? Digite sua senha antiga uma última vez.</p>
          </div>

          @if (error()) {
            <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
          }

          <button type="submit" class="wd-button" [disabled]="form.invalid || busy()">
            {{ busy() ? 'Entrando…' : 'Entrar' }}
          </button>
        </form>

        <p class="mt-4 text-center text-sm text-fg-muted">
          Perdeu a chave? <a routerLink="/recuperar" class="font-semibold text-accent">Gerar outra com a frase de 12 palavras</a>
        </p>
        <p class="mt-2 text-center text-sm text-fg-muted">
          Ainda não tem conta? <a routerLink="/cadastro" class="font-semibold text-accent">Criar conta</a>
        </p>
      }
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
    accessKey: ['', Validators.required],
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Chave recebida no último login de conta antiga (mostrada uma vez). */
  protected readonly newKey = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const login = this.form.controls.login.value.trim();
      const secret = this.form.controls.accessKey.value.trim();
      if (secret.startsWith(ACCESS_KEY_PREFIX)) {
        await this.auth.login(login, secret);
        await this.continue();
      } else {
        this.newKey.set(await this.auth.legacyLogin(login, secret));
        this.form.controls.accessKey.reset();
      }
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível entrar.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async continue(): Promise<void> {
    this.newKey.set(null);
    // Conta criada antes da frase de recuperação: gera a frase antes de seguir.
    await this.router.navigateByUrl(this.auth.needsRecoveryPhrase() ? '/frase-de-recuperacao' : safeReturnUrl(this.voltar()));
  }
}

/** Só aceita caminhos internos, para o parâmetro "voltar" não virar um redirecionamento para outro site. */
export function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/diarios';
}
