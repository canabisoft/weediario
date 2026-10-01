import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { checkPhrase, deriveVerifier } from '../core/auth/recovery-phrase';

/**
 * Recuperação de conta pela frase de 12 palavras. A frase é conferida e derivada aqui no navegador; o servidor
 * recebe só o derivado. Ao recuperar, a senha é trocada e as outras sessões são encerradas.
 */
@Component({
  selector: 'app-recover-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Recuperar conta</h1>
      <p class="mt-1 text-fg-muted">
        Use as 12 palavras que você anotou ao criar a conta. Elas não saem deste aparelho: o servidor só recebe um
        código derivado delas.
      </p>

      <form class="wd-card mt-6 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="wd-label" for="username">Apelido</label>
          <input id="username" class="wd-input" formControlName="username" autocomplete="username" autocapitalize="none" spellcheck="false" />
        </div>
        <div>
          <label class="wd-label" for="phrase">Frase de recuperação</label>
          <textarea
            id="phrase"
            rows="3"
            class="wd-input py-2 font-mono text-base"
            formControlName="phrase"
            autocomplete="off"
            autocapitalize="none"
            spellcheck="false"
            placeholder="12 palavras, na ordem, separadas por espaço"
          ></textarea>
        </div>
        <div>
          <label class="wd-label" for="new-password">Senha nova</label>
          <input id="new-password" type="password" class="wd-input" formControlName="newPassword" autocomplete="new-password" minlength="12" />
          <p class="mt-1 text-xs text-fg-muted">Mínimo de 12 caracteres. Todas as outras sessões abertas serão encerradas.</p>
        </div>

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }

        <button type="submit" class="wd-button" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Conferindo…' : 'Recuperar e entrar' }}
        </button>
      </form>

      <p class="mt-4 text-center text-sm text-fg-muted">
        Lembrou a senha? <a routerLink="/entrar" class="font-semibold text-accent">Entrar</a>
      </p>
    </section>
  `,
})
export class RecoverPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    phrase: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
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
      const { username, phrase, newPassword } = this.form.getRawValue();
      const checked = await checkPhrase(phrase);
      if (!checked.ok) {
        this.error.set(checked.message);
        return;
      }
      await this.auth.recover(username.trim(), await deriveVerifier(checked.phrase), newPassword);
      this.form.reset();
      await this.router.navigateByUrl('/diarios');
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível recuperar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }
}
