import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { TERMS_VERSION } from '../legal/terms.page';
import { PrivacyNote } from '../shared/privacy-note';
import { AccessKeyReveal } from './access-key-reveal';
import { RecoveryPhraseCard } from './recovery-phrase-card';

/**
 * Cadastro em três passos: apelido + termos, frase de recuperação (gerada no navegador) e a chave de acesso (gerada
 * pelo servidor, mostrada uma vez). Não há e-mail nem senha.
 */
@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, PrivacyNote, RecoveryPhraseCard, AccessKeyReveal],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Criar conta</h1>
      <p class="mt-1 text-fg-muted">Pra galera você é só o seu apelido. Nada de nome real, e-mail ou senha.</p>

      @switch (step()) {
        @case ('dados') {
          <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="next()">
            <div>
              <label class="wd-label" for="username">Apelido público</label>
              <input id="username" class="wd-input" formControlName="username" autocomplete="username" maxlength="30" />
              <p class="mt-1 text-xs text-fg-muted">3 a 30 caracteres: letras, números, "_" e ".". Não use seu nome real.</p>
            </div>

            <label class="flex items-start gap-2 text-sm">
              <input type="checkbox" class="mt-0.5 size-5" formControlName="acceptTerms" />
              <span>
                Li e aceito os <a routerLink="/termos" target="_blank" class="font-semibold text-accent">Termos de Uso</a>
                (versão {{ termsVersion }}), incluindo a proibição de qualquer comércio. Tenho 18 anos ou mais.
              </span>
            </label>

            <button type="submit" class="wd-button" [disabled]="form.invalid">Continuar</button>
          </form>
        }
        @case ('frase') {
          <section class="wd-card mt-6 grid gap-4">
            <div>
              <h2 class="text-lg font-semibold">Sua frase de recuperação</h2>
              <p class="text-sm text-fg-muted">Passo 2 de 3 · conta {{ form.controls.username.value }}</p>
            </div>
            <app-recovery-phrase-card confirmLabel="Criar conta" [disabled]="busy()" (verified)="create($event)" />
            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
            <button type="button" class="justify-self-start text-sm text-fg-muted underline" (click)="step.set('dados')">
              Voltar
            </button>
          </section>
        }
        @case ('chave') {
          <section class="wd-card mt-6 grid gap-4">
            <div>
              <h2 class="text-lg font-semibold">Conta criada!</h2>
              <p class="text-sm text-fg-muted">Passo 3 de 3 · guarde sua chave de acesso</p>
            </div>
            <app-access-key-reveal [username]="form.controls.username.value" [accessKey]="accessKey()" (done)="finish()" />
          </section>
        }
      }

      @if (step() !== 'chave') {
        <app-privacy-note class="mt-4" title="Relaxa, aqui é tranquilo" />

        <p class="mt-4 text-center text-sm text-fg-muted">
          Já tem conta? <a routerLink="/entrar" class="font-semibold text-accent">Entrar</a>
        </p>
      }
    </section>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_.]+$/)]],
    acceptTerms: [false, [Validators.requiredTrue]],
  });
  protected readonly termsVersion = TERMS_VERSION;
  protected readonly step = signal<'dados' | 'frase' | 'chave'>('dados');
  protected readonly accessKey = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected next(): void {
    if (this.form.valid) {
      this.error.set(null);
      this.step.set('frase');
    }
  }

  protected async create(recoveryVerifier: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.accessKey.set(await this.auth.register(this.form.controls.username.value, recoveryVerifier, TERMS_VERSION));
      this.step.set('chave');
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível criar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async finish(): Promise<void> {
    this.accessKey.set('');
    await this.router.navigateByUrl('/perfil');
  }
}
