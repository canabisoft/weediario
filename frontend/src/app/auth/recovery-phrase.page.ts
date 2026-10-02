import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { RecoveryPhraseCard } from './recovery-phrase-card';

/**
 * Gera a frase de recuperação (contas criadas antes dela) ou troca por uma nova. Pede a senha atual: só com a sessão,
 * alguém com o aparelho destravado poderia trocar a frase e tomar a conta.
 */
@Component({
  selector: 'app-recovery-phrase-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, RecoveryPhraseCard],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Frase de recuperação</h1>
      @if (done()) {
        <div class="wd-card mt-6 grid gap-2">
          <p class="font-semibold text-accent">Pronto! Sua frase de recuperação está ativa.</p>
          @if (hadPhrase()) {
            <p class="text-sm text-fg-muted">A frase anterior não vale mais.</p>
          }
          <a routerLink="/perfil" class="text-sm underline">Voltar ao perfil</a>
        </div>
      } @else {
        @if (hadPhrase()) {
          <p class="mt-1 text-fg-muted">
            Sua conta já tem uma frase. Gerar outra invalida a anterior — útil se você acha que alguém viu as palavras.
          </p>
        } @else {
          <p class="mt-1 text-fg-muted">
            Sua conta ainda não tem uma frase de recuperação. Com ela você volta para a conta mesmo se esquecer a senha
            e não tiver um e-mail cadastrado. Leva um minuto.
          </p>
        }

        @if (started()) {
          <section class="wd-card mt-6 grid gap-4">
            <div>
              <label class="wd-label" for="current-password">Senha atual</label>
              <input
                id="current-password"
                name="currentPassword"
                type="password"
                class="wd-input"
                autocomplete="current-password"
                [ngModel]="currentPassword()"
                (ngModelChange)="currentPassword.set($event)"
              />
            </div>
            <app-recovery-phrase-card
              [confirmLabel]="hadPhrase() ? 'Trocar a frase' : 'Ativar a frase'"
              [disabled]="!currentPassword() || busy()"
              (verified)="save($event)"
            />
            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
          </section>
        } @else {
          <button type="button" class="wd-button mt-6" (click)="started.set(true)">
            {{ hadPhrase() ? 'Gerar uma frase nova' : 'Gerar minha frase' }}
          </button>
        }
      }
    </section>
  `,
})
export class RecoveryPhrasePage {
  private readonly auth = inject(AuthService);

  /** Foto de quando a página abriu (depois de salvar, o perfil passa a ter frase). */
  protected readonly hadPhrase = signal(this.auth.me()?.hasRecoveryPhrase ?? false);
  protected readonly started = signal(!this.hadPhrase());
  protected readonly currentPassword = signal('');
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async save(recoveryVerifier: string): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.changeRecoveryPhrase(recoveryVerifier, this.currentPassword());
      this.currentPassword.set('');
      this.done.set(true);
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível salvar a frase.'));
    } finally {
      this.busy.set(false);
    }
  }
}
