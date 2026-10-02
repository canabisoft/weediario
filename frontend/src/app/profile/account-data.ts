import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountApi } from '../core/api/account.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';

type Panel = 'none' | 'export' | 'delete';

/**
 * Seus dados (LGPD): baixar tudo num .zip (dados.json + fotos) e apagar a conta. Os dois pedem a senha atual; apagar
 * também pede o apelido digitado, para ninguém apagar sem querer.
 */
@Component({
  selector: 'app-account-data',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <section class="wd-card mt-6 grid gap-4">
      <h2 class="text-lg font-semibold">Seus dados</h2>
      <p class="text-sm text-fg-muted">
        Você pode baixar tudo o que a sua conta guarda ou apagar a conta de vez, quando quiser.
      </p>
      <div class="flex flex-wrap gap-2">
        <button type="button" class="wd-button bg-surface-raised text-fg" (click)="toggle('export')">Baixar meus dados</button>
        <button type="button" class="wd-button bg-surface-raised text-danger" (click)="toggle('delete')">Apagar minha conta</button>
      </div>

      @if (panel() === 'export') {
        <form class="grid gap-3 rounded-xl border border-line p-3" (submit)="$event.preventDefault(); download()">
          <p class="text-sm text-fg-muted">
            Um arquivo .zip com um <code>dados.json</code> (conta, diários, receitas, tratamento, comentários…) e as suas
            fotos, já sem metadados.
          </p>
          <div>
            <label class="wd-label" for="data-export-password">Senha atual</label>
            <input id="data-export-password" name="password" type="password" class="wd-input" autocomplete="current-password"
                   [ngModel]="password()" (ngModelChange)="password.set($event)" />
          </div>
          <button type="submit" class="wd-button" [disabled]="!password() || busy()">{{ busy() ? 'Preparando…' : 'Baixar .zip' }}</button>
        </form>
      }

      @if (panel() === 'delete') {
        <form class="grid gap-3 rounded-xl border border-danger/50 bg-danger/5 p-3" (submit)="$event.preventDefault(); remove()">
          <div class="text-sm">
            <p class="font-semibold text-danger">Isso não tem volta.</p>
            <ul class="mt-1 list-disc pl-5 text-fg-muted">
              <li>Somem a conta, os diários (com as fotos), receitas, setups, avaliações, comentários e o tratamento, incluindo pacientes que você acompanha como titular.</li>
              <li>Itens que você cadastrou no catálogo aberto ficam, sem o seu nome.</li>
              <li>Registros de moderação sobre a conta ficam por 5 anos, sem vínculo com ela (exigência legal).</li>
              <li>Reações e denúncias já são anônimas e continuam como estão.</li>
            </ul>
            <p class="mt-1 text-fg-muted">Quer guardar algo? Baixe seus dados antes.</p>
          </div>
          <div>
            <label class="wd-label" for="data-delete-username">Digite o seu apelido ({{ username() }})</label>
            <input id="data-delete-username" name="username" class="wd-input" autocomplete="off" autocapitalize="none" spellcheck="false"
                   [ngModel]="typedUsername()" (ngModelChange)="typedUsername.set($event)" />
          </div>
          <div>
            <label class="wd-label" for="data-delete-password">Senha atual</label>
            <input id="data-delete-password" name="password" type="password" class="wd-input" autocomplete="current-password"
                   [ngModel]="password()" (ngModelChange)="password.set($event)" />
          </div>
          <button type="submit" class="wd-button bg-danger text-white"
                  [disabled]="!password() || typedUsername().trim().toLowerCase() !== username().toLowerCase() || busy()">
            {{ busy() ? 'Apagando…' : 'Apagar minha conta para sempre' }}
          </button>
        </form>
      }

      @if (error()) {
        <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
      }
    </section>
  `,
})
export class AccountDataPanel {
  private readonly api = inject(AccountApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly username = input.required<string>();

  protected readonly panel = signal<Panel>('none');
  protected readonly password = signal('');
  protected readonly typedUsername = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected toggle(panel: Panel): void {
    this.panel.set(this.panel() === panel ? 'none' : panel);
    this.password.set('');
    this.typedUsername.set('');
    this.error.set(null);
  }

  protected async download(): Promise<void> {
    await this.run(async () => {
      const zip = await firstValueFrom(this.api.exportData(this.password()));
      const url = URL.createObjectURL(zip);
      const link = document.createElement('a');
      link.href = url;
      link.download = `weediario-${this.username()}-dados.zip`;
      link.click();
      URL.revokeObjectURL(url);
      this.toggle('none');
    });
  }

  protected async remove(): Promise<void> {
    await this.run(async () => {
      await firstValueFrom(this.api.deleteAccount(this.password(), this.typedUsername().trim()));
      this.auth.clear();
      await this.router.navigateByUrl('/', { replaceUrl: true });
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(await messageOf(error));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Com {@code responseType: 'blob'}, o corpo do erro também chega como Blob: lê o JSON dele antes de mostrar. */
async function messageOf(error: unknown): Promise<string> {
  const body = (error as { error?: unknown } | null)?.error;
  if (body instanceof Blob) {
    try {
      const problem = JSON.parse(await body.text()) as { detail?: string };
      if (problem.detail) {
        return problem.detail;
      }
    } catch {
      // Segue para a mensagem genérica.
    }
  }
  return problemMessage(error, 'Não foi possível concluir.');
}
