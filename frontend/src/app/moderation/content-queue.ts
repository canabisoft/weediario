import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import {
  AccountAction,
  ContentType,
  ModerationApi,
  ModerationReason,
  QueueItem,
  ReportReason,
  Verdict,
} from '../core/api/moderation.api';
import { problemMessage } from '../core/api/problem';
import { timeAgo } from '../notifications/notification-text';
import { Icon } from '../shared/icon';
import { REPORT_REASON_LABEL } from '../shared/report-button';

export const CONTENT_TYPE_LABEL: Record<ContentType, string> = {
  COMMENT: 'Comentário em diário',
  DIARY: 'Diário',
  REVIEW: 'Review de strain',
  RECIPE: 'Receita',
  RECIPE_COMMENT: 'Comentário em receita',
  PROFILE: 'Perfil (bio e foto)',
};

const SHORT_REASON: Record<ReportReason, string> = {
  ILLEGAL_COMMERCE: 'Comércio ilegal',
  TRAFFICKING_APOLOGY: 'Apologia ao tráfico',
  INAPPROPRIATE: 'Impróprio',
  SPAM: 'Spam',
  OTHER: 'Outro',
};

const REASONS = Object.keys(SHORT_REASON) as ReportReason[];

/**
 * Fila unificada: tudo que foi denunciado ou retido pelo filtro anti-comércio. Retidos primeiro, depois os mais
 * denunciados. Quem denunciou nunca aparece. Cada decisão vai para o log de moderação.
 */
@Component({
  selector: 'app-content-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    @if (page(); as result) {
      <ul class="grid gap-3">
        @for (item of result.items; track item.type + item.id) {
          <li class="wd-card grid gap-3">
            <div class="flex flex-wrap items-center gap-2 text-sm">
              <span class="rounded-full bg-surface-raised px-2 py-0.5 text-xs font-semibold">{{ typeLabel[item.type] }}</span>
              @if (item.held) {
                <span class="inline-flex items-center gap-1 rounded-full bg-danger/15 px-2 py-0.5 text-xs font-semibold text-danger">
                  <app-icon name="eyeOff" class="size-3.5" />
                  {{ item.cause === 'AUTO_FILTER' ? 'Retido pelo filtro' + (item.detail ? ': ' + item.detail : '') : 'Fora do ar por denúncias' }}
                </span>
              }
              <span class="text-xs text-fg-muted">{{ when(item.since) }}</span>
            </div>

            @if (!item.exists) {
              <p class="text-sm text-fg-muted">O conteúdo já foi apagado pelo autor. Ele sai da fila na próxima limpeza.</p>
            } @else {
              <p class="text-sm">
                @if (item.author) {
                  <a [routerLink]="['/growers', item.author]" class="font-semibold">{{ item.author }}</a>
                  @if (item.authorStatus && item.authorStatus !== 'ACTIVE') {
                    <span class="ml-1 rounded-full bg-danger/15 px-2 text-xs text-danger">{{ item.authorStatus === 'BANNED' ? 'banida' : 'suspensa' }}</span>
                  }
                }
                @if (item.title) {
                  <span class="text-fg-muted"> · </span>
                  @if (link(item); as target) {
                    <a [routerLink]="target" class="text-accent">{{ item.title }}</a>
                  } @else {
                    <span>{{ item.title }}</span>
                  }
                }
              </p>
              @if (item.text) {
                <p class="max-h-48 overflow-auto whitespace-pre-line break-words rounded-xl bg-surface-raised p-3 text-sm">{{ item.text }}</p>
              }
            }

            @if (item.reports) {
              <p class="flex flex-wrap gap-2 text-xs">
                <span class="inline-flex items-center gap-1 font-semibold"><app-icon name="flag" class="size-3.5" /> {{ item.reports }} {{ item.reports === 1 ? 'denúncia' : 'denúncias' }}:</span>
                @for (reason of reasonsOf(item); track reason.key) {
                  <span class="rounded-full bg-warn/15 px-2">{{ reason.label }} ({{ reason.count }})</span>
                }
              </p>
            }

            @if (item.exists) {
              <div class="grid gap-2 rounded-xl border border-line p-3 sm:grid-cols-[auto_1fr]">
                <label class="wd-label sm:col-span-2" [for]="'reason-' + item.id">Motivo (fica no log de moderação)</label>
                <select [id]="'reason-' + item.id" class="wd-input text-base" [value]="reasonFor(item)" (change)="setReason(item, $any($event.target).value)">
                  @for (reason of reasons; track reason) {
                    <option [value]="reason">{{ reasonLabel[reason] }}</option>
                  }
                </select>
                <input class="wd-input" maxlength="500" placeholder="Observação (opcional)" [attr.aria-label]="'Observação'"
                       [value]="notes()[key(item)] ?? ''" (input)="setNote(item, $any($event.target).value)" />
                <div class="flex flex-wrap gap-2 sm:col-span-2">
                  <button type="button" class="wd-button min-h-10 bg-surface-raised text-fg" [disabled]="busy()" (click)="decide(item, 'KEEP', 'NONE')">
                    <app-icon name="check" class="size-4" /> Manter (improcedente)
                  </button>
                  <button type="button" class="wd-button min-h-10 bg-surface-raised text-danger" [disabled]="busy()" (click)="decide(item, 'REMOVE', 'NONE')">
                    <app-icon name="trash" class="size-4" /> Remover
                  </button>
                  <button type="button" class="wd-button min-h-10 bg-surface-raised text-danger" [disabled]="busy()" (click)="decide(item, 'REMOVE', 'SUSPEND')">
                    Remover e suspender autor
                  </button>
                  @if (confirmBan() === key(item)) {
                    <button type="button" class="wd-button min-h-10 bg-danger text-white" [disabled]="busy()" (click)="decide(item, 'REMOVE', 'BAN')">
                      Confirmar banimento
                    </button>
                    <button type="button" class="wd-button min-h-10 bg-surface-raised text-fg" (click)="confirmBan.set(null)">Cancelar</button>
                  } @else {
                    <button type="button" class="wd-button min-h-10 bg-surface-raised text-danger" (click)="confirmBan.set(key(item))">
                      Remover e banir autor
                    </button>
                  }
                </div>
              </div>
            }
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nada denunciado nem retido. A comunidade está em paz.</li>
        }
      </ul>
      @if (result.totalPages > 1) {
        <nav class="mt-4 flex items-center justify-between" aria-label="Páginas">
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page === 0" (click)="pageIndex.set(result.page - 1)">Anterior</button>
          <span class="text-sm text-fg-muted">{{ result.page + 1 }} de {{ result.totalPages }}</span>
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page + 1 >= result.totalPages" (click)="pageIndex.set(result.page + 1)">Próxima</button>
        </nav>
      }
    } @else {
      <div class="h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
    @if (message()) {
      <p role="status" class="mt-3 text-sm">{{ message() }}</p>
    }
  `,
})
export class ContentQueue {
  private readonly api = inject(ModerationApi);

  readonly changed = output<void>();

  protected readonly typeLabel = CONTENT_TYPE_LABEL;
  protected readonly reasons = REASONS;
  protected readonly reasonLabel = REPORT_REASON_LABEL;
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly confirmBan = signal<string | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly notes = signal<Record<string, string>>({});
  private readonly reload = signal(0);
  private readonly chosenReasons = signal<Record<string, ReportReason>>({});

  protected readonly page = toSignal(
    combineLatest([toObservable(this.pageIndex), toObservable(this.reload)]).pipe(
      switchMap(([page]) => this.reloadable(page)),
    ),
  );

  private reloadable(page: number): Observable<Page<QueueItem>> {
    return this.api.queue(page).pipe(
      catchError(() => of<Page<QueueItem>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })),
    );
  }

  protected key(item: QueueItem): string {
    return item.type + ':' + item.id;
  }

  protected when(instant: string): string {
    return timeAgo(instant);
  }

  protected link(item: QueueItem): string[] | null {
    if (!item.linkId) return null;
    switch (item.type) {
      case 'DIARY':
      case 'COMMENT':
        return ['/diarios', item.linkId];
      case 'REVIEW':
        return ['/strains', item.linkId];
      case 'RECIPE':
      case 'RECIPE_COMMENT':
        return ['/receitas', item.linkId];
      case 'PROFILE':
        return ['/growers', item.linkId];
    }
  }

  protected reasonsOf(item: QueueItem): { key: string; label: string; count: number }[] {
    return (Object.entries(item.reasons) as [ReportReason, number][])
      .sort((a, b) => b[1] - a[1])
      .map(([key, count]) => ({ key, label: SHORT_REASON[key], count }));
  }

  /** Motivo pré-selecionado: o mais denunciado; retido pelo filtro, comércio ilegal. */
  protected reasonFor(item: QueueItem): ReportReason {
    return this.chosenReasons()[this.key(item)] ?? this.reasonsOf(item)[0]?.key as ReportReason ?? 'ILLEGAL_COMMERCE';
  }

  protected setReason(item: QueueItem, reason: ReportReason): void {
    this.chosenReasons.update((all) => ({ ...all, [this.key(item)]: reason }));
  }

  protected setNote(item: QueueItem, note: string): void {
    this.notes.update((all) => ({ ...all, [this.key(item)]: note }));
  }

  protected decide(item: QueueItem, verdict: Verdict, account: AccountAction): void {
    const note = this.notes()[this.key(item)]?.trim() || undefined;
    const reason: ModerationReason | undefined = verdict === 'REMOVE' ? this.reasonFor(item) : undefined;
    this.busy.set(true);
    this.message.set(null);
    this.api.decide({ targetType: item.type, targetId: item.id, verdict, account, reason, note }).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmBan.set(null);
        this.message.set(verdict === 'KEEP' ? 'Mantido e de volta ao ar.' : account === 'NONE' ? 'Removido.'
          : account === 'BAN' ? 'Removido e conta banida.' : 'Removido e conta suspensa.');
        this.reload.update((n) => n + 1);
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(false);
        this.message.set(problemMessage(error));
      },
    });
  }
}
