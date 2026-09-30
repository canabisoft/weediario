import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, of, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { ModerationApi, ReportedComment } from '../core/api/moderation.api';
import { problemMessage } from '../core/api/problem';
import { timeAgo } from '../notifications/notification-text';
import { Icon } from '../shared/icon';

/**
 * Fila de comentários denunciados: ocultados automaticamente (3+ denúncias) primeiro. A moderação decide
 * manter (as denúncias são descartadas) ou remover. Quem denunciou nunca aparece.
 */
@Component({
  selector: 'app-comment-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    @if (page(); as result) {
      <ul class="grid gap-3">
        @for (comment of result.items; track comment.id) {
          <li class="wd-card grid gap-3">
            <p class="flex flex-wrap items-center gap-2 text-sm">
              <a [routerLink]="['/growers', comment.author]" class="font-semibold">{{ comment.author }}</a>
              <span class="text-fg-muted">em</span>
              <a [routerLink]="['/diarios', comment.diaryId]" class="truncate text-accent">{{ comment.diaryTitle }}</a>
              <time class="text-xs text-fg-muted" [attr.datetime]="comment.createdAt">{{ when(comment.createdAt) }}</time>
            </p>
            <p class="whitespace-pre-line break-words rounded-xl bg-surface-raised p-3">{{ comment.body }}</p>
            <p class="flex flex-wrap gap-2 text-xs">
              <span class="inline-flex items-center gap-1 rounded-full bg-warn/15 px-2 py-0.5 font-semibold text-warn">
                <app-icon name="flag" class="size-3.5" /> {{ comment.reports }} {{ comment.reports === 1 ? 'denúncia' : 'denúncias' }}
              </span>
              @if (comment.hidden) {
                <span class="inline-flex items-center gap-1 rounded-full bg-danger/15 px-2 py-0.5 font-semibold text-danger">
                  <app-icon name="eyeOff" class="size-3.5" /> Oculto automaticamente
                </span>
              }
            </p>
            <div class="flex flex-wrap gap-2">
              <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy() === comment.id" (click)="keep(comment)">
                <app-icon name="check" class="size-5" /> Manter
              </button>
              @if (confirming() === comment.id) {
                <button type="button" class="wd-button bg-danger text-white" [disabled]="busy() === comment.id" (click)="remove(comment)">
                  <app-icon name="trash" class="size-5" /> Confirmar remoção
                </button>
                <button type="button" class="wd-button bg-surface-raised text-fg" (click)="confirming.set(null)">Cancelar</button>
              } @else {
                <button type="button" class="wd-button bg-surface-raised text-danger" (click)="confirming.set(comment.id)">
                  <app-icon name="trash" class="size-5" /> Remover
                </button>
              }
            </div>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nenhum comentário denunciado. A comunidade está em paz.</li>
        }
      </ul>
      @if (result.totalPages > 1) {
        <nav class="mt-4 flex items-center justify-between" aria-label="Páginas">
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page === 0" (click)="goTo(result.page - 1)">Anterior</button>
          <span class="text-sm text-fg-muted">{{ result.page + 1 }} de {{ result.totalPages }}</span>
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page + 1 >= result.totalPages" (click)="goTo(result.page + 1)">Próxima</button>
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
export class CommentQueue {
  private readonly api = inject(ModerationApi);

  /** Avisa a página para atualizar os contadores das abas. */
  readonly changed = output<void>();

  protected readonly busy = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  private readonly page$ = new BehaviorSubject(0);

  protected readonly page = toSignal(
    this.page$.pipe(
      switchMap((page) =>
        this.api.reportedComments(page).pipe(
          catchError(() => of<Page<ReportedComment>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })),
        ),
      ),
    ),
  );

  protected when(instant: string): string {
    return timeAgo(instant);
  }

  protected goTo(page: number): void {
    this.page$.next(page);
  }

  protected keep(comment: ReportedComment): void {
    this.act(comment, this.api.keepComment(comment.id), 'Comentário mantido e denúncias descartadas.');
  }

  protected remove(comment: ReportedComment): void {
    this.act(comment, this.api.removeComment(comment.id), 'Comentário removido.');
  }

  private act(comment: ReportedComment, request: ReturnType<ModerationApi['keepComment']>, done: string): void {
    this.busy.set(comment.id);
    request.subscribe({
      next: () => {
        this.busy.set(null);
        this.confirming.set(null);
        this.message.set(done);
        this.page$.next(this.page$.value);
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(null);
        this.message.set(problemMessage(error));
      },
    });
  }
}
