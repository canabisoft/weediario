import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { ContentType, ModerationApi, ReportReason } from '../core/api/moderation.api';
import { problemMessage } from '../core/api/problem';
import { Icon } from './icon';

export const REPORT_REASON_LABEL: Record<ReportReason, string> = {
  ILLEGAL_COMMERCE: 'Comércio ilegal (venda, troca, contato pra negócio)',
  TRAFFICKING_APOLOGY: 'Apologia ao tráfico',
  INAPPROPRIATE: 'Conteúdo impróprio ou ofensivo',
  SPAM: 'Spam',
  OTHER: 'Outro motivo',
};

const REASONS = Object.keys(REPORT_REASON_LABEL) as ReportReason[];
let nextId = 0;

/**
 * "Denunciar" com escolha do motivo. A denúncia é anônima: guardamos só um pseudônimo de quem denunciou, que
 * ninguém (nem a moderação) consegue ligar à conta. Com 3 denúncias o conteúdo sai do ar até a moderação decidir.
 */
@Component({
  selector: 'app-report-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'inline-block' },
  template: `
    @if (!open()) {
      @if (done()) {
        <span class="text-xs text-fg-muted" role="status">{{ done() }}</span>
      } @else {
        <button type="button" class="inline-flex items-center gap-1 text-xs text-fg-muted hover:text-danger" (click)="open.set(true)">
          <app-icon name="flag" class="size-3.5" /> {{ label() }}
        </button>
      }
    } @else {
      <form class="mt-2 grid gap-2 rounded-xl border border-line bg-surface p-3 text-sm" (submit)="$event.preventDefault(); send()">
        <fieldset>
          <legend class="font-semibold">Por que você está denunciando?</legend>
          <div class="mt-2 grid gap-1">
            @for (reason of reasons; track reason) {
              <label class="flex items-start gap-2">
                <input type="radio" class="mt-0.5 size-4" [name]="group" [value]="reason" [checked]="chosen() === reason" (change)="chosen.set(reason)" />
                {{ reasonLabel[reason] }}
              </label>
            }
          </div>
        </fieldset>
        <p class="text-xs text-fg-muted">A denúncia é anônima. Com 3 denúncias, o conteúdo sai do ar até a moderação revisar.</p>
        @if (error()) {
          <p role="alert" class="text-xs text-danger">{{ error() }}</p>
        }
        <div class="flex gap-2">
          <button type="submit" class="wd-button min-h-9 px-3 text-sm" [disabled]="!chosen() || busy()">Enviar denúncia</button>
          <button type="button" class="wd-button min-h-9 bg-surface-raised px-3 text-sm text-fg" (click)="open.set(false)">Cancelar</button>
        </div>
      </form>
    }
  `,
})
export class ReportButton {
  private readonly api = inject(ModerationApi);

  readonly targetType = input.required<ContentType>();
  readonly targetId = input.required<string>();
  readonly label = input('Denunciar');
  /** {@code true} quando esta denúncia tirou o conteúdo do ar. */
  readonly reported = output<boolean>();

  protected readonly reasons = REASONS;
  protected readonly reasonLabel = REPORT_REASON_LABEL;
  protected readonly group = `report-reason-${nextId++}`;
  protected readonly open = signal(false);
  protected readonly chosen = signal<ReportReason | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal<string | null>(null);

  protected send(): void {
    const reason = this.chosen();
    if (!reason) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.report(this.targetType(), this.targetId(), reason).subscribe({
      next: ({ hidden }) => {
        this.busy.set(false);
        this.open.set(false);
        this.done.set('Denúncia enviada. Valeu por cuidar da comunidade.');
        this.reported.emit(hidden);
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
