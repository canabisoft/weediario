import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { TERMS_VERSION } from '../legal/terms.page';
import { PrivacyNote } from '../shared/privacy-note';

@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PrivacyNote],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Criar conta</h1>
      <p class="mt-1 text-fg-muted">Pra galera você é só o seu apelido. Nada de nome real.</p>

      <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="wd-label" for="username">Apelido público</label>
          <input id="username" class="wd-input" formControlName="username" autocomplete="nickname" maxlength="30" />
          <p class="mt-1 text-xs text-fg-muted">3 a 30 caracteres: letras, números, "_" e ".". Não use seu nome real.</p>
        </div>
        <div>
          <label class="wd-label" for="email">E-mail</label>
          <input id="email" type="email" class="wd-input" formControlName="email" autocomplete="email" />
          <p class="mt-1 text-xs text-fg-muted">Nunca é exibido. Serve só para entrar na conta.</p>
        </div>
        <div>
          <label class="wd-label" for="password">Senha</label>
          <input
            id="password"
            type="password"
            class="wd-input"
            formControlName="password"
            autocomplete="new-password"
            minlength="12"
          />
          <p class="mt-1 text-xs text-fg-muted">Mínimo de 12 caracteres. Uma frase longa é mais segura e fácil de lembrar.</p>
        </div>

        <label class="flex items-start gap-2 text-sm">
          <input type="checkbox" class="mt-0.5 size-5" formControlName="acceptTerms" />
          <span>
            Li e aceito os <a routerLink="/termos" target="_blank" class="font-semibold text-accent">Termos de Uso</a>
            (versão {{ termsVersion }}), incluindo a proibição de qualquer comércio. Tenho 18 anos ou mais.
          </span>
        </label>

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }

        <button type="submit" class="wd-button" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Criando…' : 'Criar conta' }}
        </button>
      </form>

      <app-privacy-note class="mt-4" title="Relaxa, aqui é tranquilo" />

      <p class="mt-4 text-center text-sm text-fg-muted">
        Já tem conta? <a routerLink="/entrar" class="font-semibold text-accent">Entrar</a>
      </p>
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_.]+$/)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    acceptTerms: [false, [Validators.requiredTrue]],
  });
  protected readonly termsVersion = TERMS_VERSION;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const { username, email, password } = this.form.getRawValue();
      await this.auth.register(username, email, password, TERMS_VERSION);
      await this.router.navigateByUrl('/perfil');
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível criar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }
}
