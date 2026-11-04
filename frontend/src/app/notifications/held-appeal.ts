import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NotificationItem, NotificationsApi } from '../core/api/notifications.api';
import { problemMessage } from '../core/api/problem';

const MIN_LENGTH = 10;
const MAX_LENGTH = 2000;
const deadline = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

/**
 * Manifestação do autor sobre um conteúdo retido (Termos de Uso, cláusula 9): uma vez, até o prazo de 5 dias que a
 * API informa. A moderação lê junto do conteúdo antes de decidir.
 */
@Component({
  selector: 'app-held-appeal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sent() || item().appealed) {
      <p class="text-sm text-fg-muted">Sua versão foi enviada. A moderação lê antes de decidir.</p>
    } @else if (!item().appealUntil) {
      <p class="text-sm text-fg-muted">A moderação já decidiu sobre este conteúdo.</p>
    } @else if (expired()) {
      <p class="text-sm text-fg-muted">O prazo de 5 dias para mandar a sua versão terminou.</p>
    } @else {
      <form class="grid gap-2" (submit)="$event.preventDefault(); send()">
        <label class="text-sm" [for]="fieldId()">
          Conte a sua versão à moderação até <strong>{{ until() }}</strong
          >. Ela lê antes de decidir.
        </label>
        <textarea
          [id]="fieldId()"
          name="appeal"
          rows="3"
          class="wd-input py-2 text-base"
          [attr.maxlength]="max"
          [value]="text()"
          (input)="text.set($any($event.target).value)"
          placeholder="O que aconteceu, do seu ponto de vista"
        ></textarea>
        @if (error()) {
          <p role="alert" class="text-sm text-danger">{{ error() }}</p>
        }
        <button
          type="submit"
          class="wd-button justify-self-start px-5"
          [disabled]="busy() || text().trim().length < min"
        >
          {{ busy() ? 'Enviando…' : 'Enviar à moderação' }}
        </button>
      </form>
    }
  `,
})
export class HeldAppeal {
  private readonly api = inject(NotificationsApi);

  readonly item = input.required<NotificationItem>();

  protected readonly min = MIN_LENGTH;
  protected readonly max = MAX_LENGTH;
  protected readonly text = signal('');
  protected readonly busy = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected fieldId(): string {
    return `apelo-${this.item().id}`;
  }

  protected until(): string {
    return deadline.format(new Date(this.item().appealUntil!));
  }

  protected expired(): boolean {
    return new Date(this.item().appealUntil!).getTime() <= Date.now();
  }

  protected send(): void {
    const item = this.item();
    this.busy.set(true);
    this.error.set(null);
    this.api.appeal(item.heldType!, item.heldId!, this.text().trim()).subscribe({
      next: () => {
        this.sent.set(true);
        this.busy.set(false);
      },
      error: (error) => {
        this.error.set(problemMessage(error, 'Não foi possível enviar. Tente de novo.'));
        this.busy.set(false);
      },
    });
  }
}
