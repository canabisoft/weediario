import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { NewPassword } from '../auth/new-password';
import { AccountApi, AccountSecurity } from '../core/api/account.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';

type Panel = 'none' | 'password' | 'email';

/**
 * Segurança da conta no perfil: senha, e-mail opcional (cadastrar, trocar, remover, confirmar), frase de recuperação e
 * o histórico de mudanças sensíveis dos últimos 90 dias (sem IP, aparelho ou local). Toda mudança pede a senha atual.
 */
@Component({
  selector: 'app-account-security',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DatePipe, NewPassword],
  template: `
    <section class="wd-card mt-6 grid gap-4">
      <h2 class="text-lg font-semibold">Segurança da conta</h2>

      @if (justRegisteredWithEmail() && security()?.email && !security()?.emailVerified) {
        <p role="status" class="rounded-xl bg-accent/10 px-3 py-2 text-sm">
          Mandamos um link para confirmar o seu e-mail. Até confirmar, ele não serve para redefinir a senha.
        </p>
      }

      @if (security(); as s) {
        <dl class="grid gap-3 text-sm">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <dt class="font-semibold">Senha</dt>
              <dd class="text-fg-muted">Definida</dd>
            </div>
            <button type="button" class="text-sm font-semibold text-accent" (click)="toggle('password')">Trocar senha</button>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <dt class="font-semibold">E-mail <span class="font-normal text-fg-muted">(opcional)</span></dt>
              <dd class="text-fg-muted">
                @if (s.email) {
                  {{ s.email }} · {{ s.emailVerified ? 'confirmado' : 'aguardando confirmação' }}
                  @if (!s.emailVerified) {
                    · <button type="button" class="underline" [disabled]="busy()" (click)="resend()">reenviar link</button>
                  }
                } @else {
                  Nenhum. Sem e-mail, a frase de 12 palavras é o único jeito de recuperar a conta.
                }
              </dd>
            </div>
            <button type="button" class="text-sm font-semibold text-accent" (click)="toggle('email')">
              {{ s.email ? 'Trocar ou remover' : 'Cadastrar e-mail' }}
            </button>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <dt class="font-semibold">Frase de recuperação</dt>
              <dd class="text-fg-muted">{{ s.hasRecoveryPhrase ? 'Ativa' : 'Ainda não gerada' }}</dd>
            </div>
            <a routerLink="/frase-de-recuperacao" class="text-sm font-semibold text-accent">
              {{ s.hasRecoveryPhrase ? 'Gerar outra' : 'Gerar agora' }}
            </a>
          </div>
        </dl>

        @if (panel() === 'password') {
          <form class="grid gap-3 rounded-xl border border-line p-3" (submit)="$event.preventDefault(); changePassword()">
            <div>
              <label class="wd-label" for="sec-current">Senha atual</label>
              <input id="sec-current" name="current" type="password" class="wd-input" autocomplete="current-password"
                     [ngModel]="current()" (ngModelChange)="current.set($event)" />
            </div>
            <app-new-password id="sec-new" label="Senha nova" (changed)="newPassword.set($event)" />
            <p class="text-xs text-fg-muted">Ao trocar, todas as outras sessões (outros aparelhos) são encerradas.</p>
            <button type="submit" class="wd-button" [disabled]="!current() || !newPassword() || busy()">Salvar senha</button>
          </form>
        }

        @if (panel() === 'email') {
          <form class="grid gap-3 rounded-xl border border-line p-3" (submit)="$event.preventDefault(); changeEmail(email())">
            <div>
              <label class="wd-label" for="sec-email">E-mail novo</label>
              <input id="sec-email" name="email" type="email" class="wd-input" autocomplete="email" autocapitalize="none" spellcheck="false"
                     [ngModel]="email()" (ngModelChange)="email.set($event)" />
              <p class="mt-1 text-xs text-fg-muted">
                Fica cifrado, nunca aparece para ninguém e não serve para entrar. O endereço antigo (se confirmado) recebe
                um aviso da troca.
              </p>
            </div>
            <div>
              <label class="wd-label" for="sec-email-current">Senha atual</label>
              <input id="sec-email-current" name="current" type="password" class="wd-input" autocomplete="current-password"
                     [ngModel]="current()" (ngModelChange)="current.set($event)" />
            </div>
            <div class="flex flex-wrap gap-2">
              <button type="submit" class="wd-button" [disabled]="!email().trim() || !current() || busy()">Salvar e-mail</button>
              @if (s.email) {
                <button type="button" class="wd-button bg-surface-raised text-danger" [disabled]="!current() || busy()" (click)="changeEmail(null)">
                  Remover e-mail
                </button>
              }
            </div>
          </form>
        }

        @if (message()) {
          <p role="status" class="text-sm text-fg-muted">{{ message() }}</p>
        }
        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }

        <div>
          <h3 class="text-sm font-semibold">Histórico (últimos 90 dias)</h3>
          <p class="text-xs text-fg-muted">Só o que mudou e quando. Não registramos IP, aparelho nem local.</p>
          <ul class="mt-2 grid gap-1 text-sm">
            @for (event of s.events; track $index) {
              <li class="flex justify-between gap-3">
                <span>{{ event.description }}</span>
                <span class="shrink-0 text-fg-muted">{{ event.occurredAt | date: 'dd/MM/yyyy HH:mm' }}</span>
              </li>
            } @empty {
              <li class="text-fg-muted">Nada por aqui.</li>
            }
          </ul>
          <p class="mt-2 text-xs text-fg-muted">
            Não reconhece algo? Troque a senha agora ou recupere a conta com a frase: isso encerra todas as sessões.
          </p>
        </div>
      } @else {
        <div class="h-32 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
      }
    </section>
  `,
})
export class AccountSecurityPanel {
  private readonly api = inject(AccountApi);
  private readonly auth = inject(AuthService);

  /** Veio do cadastro com e-mail: lembra de confirmar. */
  readonly justRegisteredWithEmail = input(false);

  protected readonly security = signal<AccountSecurity | null>(null);
  protected readonly panel = signal<Panel>('none');
  protected readonly current = signal('');
  protected readonly newPassword = signal<string | null>(null);
  protected readonly email = signal('');
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    void this.reload();
  }

  protected toggle(panel: Panel): void {
    this.panel.set(this.panel() === panel ? 'none' : panel);
    this.current.set('');
    this.email.set('');
    this.message.set(null);
    this.error.set(null);
  }

  protected async changePassword(): Promise<void> {
    const newPassword = this.newPassword();
    if (!newPassword) {
      return;
    }
    await this.run(async () => {
      await this.auth.changePassword(this.current(), newPassword);
      this.message.set('Senha salva. As outras sessões foram encerradas.');
    });
  }

  protected async changeEmail(email: string | null): Promise<void> {
    await this.run(async () => {
      this.security.set(await firstValueFrom(this.api.changeEmail(this.current(), email?.trim() || null)));
      this.message.set(email ? 'E-mail salvo. Confirme pelo link que acabamos de mandar.' : 'E-mail removido.');
    });
  }

  protected async resend(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.api.resendEmailVerification());
      this.message.set('Link de confirmação reenviado.');
    }, false);
  }

  private async run(action: () => Promise<void>, closePanel = true): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    try {
      await action();
      if (closePanel) {
        this.panel.set('none');
        this.current.set('');
      }
      await this.reload();
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível salvar.'));
    } finally {
      this.busy.set(false);
    }
  }

  private async reload(): Promise<void> {
    try {
      this.security.set(await firstValueFrom(this.api.security()));
    } catch {
      // O resto do perfil continua funcionando; a seção fica carregando até a próxima ação.
    }
  }
}
