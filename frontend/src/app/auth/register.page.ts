import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { TERMS_VERSION } from '../legal/terms.page';
import { PrivacyNote } from '../shared/privacy-note';
import { NewPassword } from './new-password';
import { RecoveryPhraseCard } from './recovery-phrase-card';

/**
 * Cadastro em dois passos: dados (apelido, senha, e-mail opcional e termos) e a frase de recuperação, gerada no
 * navegador. O e-mail é opcional: só serve para recuperar a senha e receber avisos de segurança, e é guardado cifrado.
 */
@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PrivacyNote, RecoveryPhraseCard, NewPassword],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Criar conta</h1>
      <p class="mt-1 text-fg-muted">Pra galera você é só o seu apelido. Nada de nome real.</p>

      @switch (step()) {
        @case ('dados') {
          <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="next()">
            <div>
              <label class="wd-label" for="username">Apelido público</label>
              <input id="username" class="wd-input" formControlName="username" autocomplete="username" maxlength="30" />
              <p class="mt-1 text-xs text-fg-muted">3 a 30 caracteres: letras, números, "_" e ".". Não use seu nome real.</p>
            </div>

            <app-new-password id="register" (changed)="password.set($event)" />

            <div>
              <label class="wd-label" for="email">E-mail <span class="font-normal text-fg-muted">(opcional)</span></label>
              <input id="email" type="email" class="wd-input" formControlName="email" autocomplete="email" autocapitalize="none" spellcheck="false" />
              <p class="mt-1 text-xs text-fg-muted">
                Só para recuperar a senha e receber avisos de segurança. Nunca aparece para ninguém, fica cifrado e não
                serve para entrar. Pode deixar em branco: a frase de 12 palavras do próximo passo já recupera a conta.
              </p>
            </div>

            <label class="flex items-start gap-2 text-sm">
              <input type="checkbox" class="mt-0.5 size-5" formControlName="acceptTerms" />
              <span>
                Li e aceito os <a routerLink="/termos" target="_blank" class="font-semibold text-accent">Termos de Uso</a>
                (versão {{ termsVersion }}), incluindo a proibição de qualquer comércio. Tenho 18 anos ou mais.
              </span>
            </label>

            <button type="submit" class="wd-button" [disabled]="form.invalid || !password()">Continuar</button>
          </form>
        }
        @case ('frase') {
          <section class="wd-card mt-6 grid gap-4">
            <div>
              <h2 class="text-lg font-semibold">Sua frase de recuperação</h2>
              <p class="text-sm text-fg-muted">Passo 2 de 2 · conta {{ form.controls.username.value }}</p>
            </div>
            <app-recovery-phrase-card confirmLabel="Criar conta" [hasEmail]="!!email()" [disabled]="busy()" (verified)="create($event)" />
            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
            <button type="button" class="justify-self-start text-sm text-fg-muted underline" (click)="step.set('dados')">
              Voltar
            </button>
          </section>
        }
      }

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
    email: ['', [Validators.email, Validators.maxLength(254)]],
    acceptTerms: [false, [Validators.requiredTrue]],
  });
  protected readonly termsVersion = TERMS_VERSION;
  protected readonly step = signal<'dados' | 'frase'>('dados');
  protected readonly password = signal<string | null>(null);
  protected readonly email = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected next(): void {
    if (this.form.valid && this.password()) {
      this.error.set(null);
      this.email.set(this.form.controls.email.value.trim() || null);
      this.step.set('frase');
    }
  }

  protected async create(recoveryVerifier: string): Promise<void> {
    const password = this.password();
    if (!password) {
      this.step.set('dados');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.register(this.form.controls.username.value, password, this.email(), recoveryVerifier, TERMS_VERSION);
      await this.router.navigate(['/perfil'], { queryParams: this.email() ? { email: 'confirmar' } : {} });
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível criar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }
}
