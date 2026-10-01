import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { ConsentStatus, TreatmentApi } from '../core/api/treatment.api';
import { problemMessage } from '../core/api/problem';
import { TERMS_VERSION } from './treatment-text';

/**
 * Termo de consentimento específico para dados de saúde (LGPD, art. 11, I), separado do cadastro da conta.
 * Cada conta aceita por si, inclusive quem é convidado como cuidador.
 */
@Component({
  selector: 'app-treatment-consent',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="wd-card grid gap-3" aria-labelledby="consent-title">
      <h2 id="consent-title" class="text-lg font-semibold">Antes de começar: seus dados de saúde</h2>
      <p class="text-sm text-fg-muted">Versão do termo: {{ version }}</p>
      <div class="grid gap-2 text-sm">
        <p><strong>O que fica guardado:</strong> os perfis que você criar (um apelido, não precisa ser o nome
          verdadeiro), os remédios com a concentração do rótulo, cada uso registrado, as notas dos sintomas, os
          efeitos que você anotar e qual conta registrou cada coisa.</p>
        <p><strong>Para que serve:</strong> só para você, e para os cuidadores que você convidar, acompanharem o
          histórico e gerarem o relatório para levar à consulta.</p>
        <p><strong>O que não fazemos:</strong> nada disso aparece no feed, no ranking, no seu perfil público ou em
          notificações para outras pessoas. Não vendemos, não compartilhamos e não usamos para anúncios ou pesquisa.
          O app não indica dose, horário nem produto.</p>
        <p><strong>Como protegemos:</strong> os textos (nomes e observações) são cifrados antes de ir para o banco e
          não guardamos logs de acesso.</p>
        <p><strong>Seus direitos:</strong> você pode baixar tudo em JSON e apagar tudo a qualquer momento, na tela do
          acompanhamento. Apagar é definitivo.</p>
      </div>
      <label class="flex items-start gap-2 text-sm">
        <input type="checkbox" class="mt-0.5 size-5" [checked]="agreed()" (change)="agreed.set($any($event.target).checked)" />
        Li e concordo com o tratamento desses dados de saúde para essas finalidades.
      </label>
      @if (error()) {
        <p role="alert" class="text-sm text-danger">{{ error() }}</p>
      }
      <button type="button" class="wd-button w-fit" [disabled]="!agreed() || busy()" (click)="accept()">Concordo e quero começar</button>
    </section>
  `,
})
export class TreatmentConsent {
  private readonly api = inject(TreatmentApi);

  readonly accepted = output<ConsentStatus>();

  protected readonly version = TERMS_VERSION;
  protected readonly agreed = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected accept(): void {
    this.busy.set(true);
    this.api.acceptConsent(TERMS_VERSION).subscribe({
      next: (status) => this.accepted.emit(status),
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
