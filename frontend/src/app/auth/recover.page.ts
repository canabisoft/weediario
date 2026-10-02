import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { checkPhrase, deriveVerifier } from '../core/auth/recovery-phrase';
import { NewPassword } from './new-password';
import { RecoveryPhraseCard } from './recovery-phrase-card';

type Mode = 'frase' | 'email';

/**
 * Recuperar a conta de dois jeitos:
 * <ul>
 *   <li><b>Frase de 12 palavras</b> (toda conta tem): define a senha nova e troca a frase por outra, porque a antiga
 *   acabou de ser digitada. As palavras são conferidas e derivadas aqui; o servidor recebe só o derivado.</li>
 *   <li><b>E-mail</b> (se a conta tiver um confirmado): manda um link de redefinição. A resposta é sempre a mesma,
 *   para não revelar se a conta tem e-mail.</li>
 * </ul>
 */
@Component({
  selector: 'app-recover-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, NewPassword, RecoveryPhraseCard],
  template: `
    <section class="mx-auto max-w-md">
      <h1 class="text-2xl font-bold">Recuperar conta</h1>

      <div class="mt-4 grid grid-cols-2 gap-2" role="tablist">
        <button type="button" role="tab" class="wd-button" [class.bg-surface-raised]="mode() !== 'frase'" [class.text-fg]="mode() !== 'frase'"
                [attr.aria-selected]="mode() === 'frase'" (click)="switchTo('frase')">
          Com a frase
        </button>
        <button type="button" role="tab" class="wd-button" [class.bg-surface-raised]="mode() !== 'email'" [class.text-fg]="mode() !== 'email'"
                [attr.aria-selected]="mode() === 'email'" (click)="switchTo('email')">
          Pelo e-mail
        </button>
      </div>

      @if (mode() === 'frase') {
        @if (oldVerifier()) {
          <section class="wd-card mt-6 grid gap-4">
            <div>
              <h2 class="text-lg font-semibold">Anote a sua frase nova</h2>
              <p class="text-sm text-fg-muted">
                A frase antiga acabou de ser digitada, então ela é trocada por esta. Depois de confirmar, a senha nova
                passa a valer e todas as outras sessões são encerradas.
              </p>
            </div>
            <app-recovery-phrase-card confirmLabel="Recuperar e entrar" [disabled]="busy()" (verified)="recover($event)" />
            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
            <button type="button" class="justify-self-start text-sm text-fg-muted underline" (click)="oldVerifier.set(null)">Voltar</button>
          </section>
        } @else {
          <p class="mt-4 text-fg-muted">
            Use as 12 palavras que você anotou ao criar a conta. Elas não saem deste aparelho: o servidor só recebe um
            código derivado delas.
          </p>
          <form class="wd-card mt-4 grid gap-4" [formGroup]="phraseForm" (ngSubmit)="checkOldPhrase()">
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
            <app-new-password id="recover" label="Senha nova" (changed)="newPassword.set($event)" />

            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
            <button type="submit" class="wd-button" [disabled]="phraseForm.invalid || !newPassword() || busy()">
              {{ busy() ? 'Conferindo…' : 'Continuar' }}
            </button>
          </form>
        }
      } @else {
        @if (emailSent()) {
          <div class="wd-card mt-6 grid gap-2" role="status">
            <p class="font-semibold">Pedido recebido.</p>
            <p class="text-sm text-fg-muted">
              Se a conta tiver um e-mail confirmado, um link para criar uma senha nova chega em instantes. Ele vale por
              30 minutos. Não chegou? Veja o spam ou recupere com a frase de 12 palavras.
            </p>
          </div>
        } @else {
          <p class="mt-4 text-fg-muted">
            Funciona se você cadastrou e confirmou um e-mail na conta. Informe só o apelido: o e-mail nunca é usado para
            procurar contas.
          </p>
          <form class="wd-card mt-4 grid gap-4" [formGroup]="emailForm" (ngSubmit)="requestEmail()">
            <div>
              <label class="wd-label" for="email-username">Apelido</label>
              <input id="email-username" class="wd-input" formControlName="username" autocomplete="username" autocapitalize="none" spellcheck="false" />
            </div>
            @if (error()) {
              <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
            }
            <button type="submit" class="wd-button" [disabled]="emailForm.invalid || busy()">
              {{ busy() ? 'Enviando…' : 'Mandar link' }}
            </button>
          </form>
        }
      }

      <p class="mt-4 text-center text-sm text-fg-muted">
        Lembrou a senha? <a routerLink="/entrar" class="font-semibold text-accent">Entrar</a>
      </p>
    </section>
  `,
})
export class RecoverPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder).nonNullable;

  protected readonly mode = signal<Mode>('frase');
  protected readonly phraseForm = this.fb.group({
    username: ['', Validators.required],
    phrase: ['', Validators.required],
  });
  protected readonly emailForm = this.fb.group({ username: ['', Validators.required] });
  protected readonly newPassword = signal<string | null>(null);
  /** Derivado da frase antiga, já conferida no navegador; as palavras em si saem da página. */
  protected readonly oldVerifier = signal<string | null>(null);
  protected readonly emailSent = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected switchTo(mode: Mode): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  protected async checkOldPhrase(): Promise<void> {
    if (this.phraseForm.invalid || !this.newPassword()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const checked = await checkPhrase(this.phraseForm.controls.phrase.value);
      if (!checked.ok) {
        this.error.set(checked.message);
        return;
      }
      this.oldVerifier.set(await deriveVerifier(checked.phrase));
      this.phraseForm.controls.phrase.reset();
    } finally {
      this.busy.set(false);
    }
  }

  protected async recover(newRecoveryVerifier: string): Promise<void> {
    const oldVerifier = this.oldVerifier();
    const newPassword = this.newPassword();
    if (!oldVerifier || !newPassword) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.recoverWithPhrase(this.phraseForm.controls.username.value.trim(), oldVerifier, newPassword, newRecoveryVerifier);
      this.oldVerifier.set(null);
      await this.router.navigateByUrl('/perfil');
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível recuperar a conta.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async requestEmail(): Promise<void> {
    if (this.emailForm.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.requestPasswordReset(this.emailForm.controls.username.value.trim());
      this.emailSent.set(true);
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível enviar agora. Tente de novo em instantes.'));
    } finally {
      this.busy.set(false);
    }
  }
}
