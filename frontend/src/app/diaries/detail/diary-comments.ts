import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { Comment } from '../../core/api/api.models';
import { problemMessage } from '../../core/api/problem';
import { SocialApi } from '../../core/api/social.api';
import { AuthService } from '../../core/auth/auth.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { ReportButton } from '../../shared/report-button';

/**
 * Conversa do diário. O texto é exibido por interpolação (escapado pelo Angular), nunca como HTML.
 */
@Component({
  selector: 'app-diary-comments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Avatar, Icon, ReportButton],
  template: `
    <h2 class="text-lg font-semibold">Comentários</h2>

    @if (comments(); as list) {
      @if (list.length === 0) {
        <p class="mt-2 text-sm text-fg-muted">Nenhum comentário ainda. Tire uma dúvida ou deixe uma dica.</p>
      }
      <ul class="mt-3 grid gap-3">
        @for (comment of list; track comment.id) {
          <li class="flex gap-3">
            <app-avatar class="size-9 text-sm" [username]="comment.author" [hasAvatar]="comment.authorHasAvatar" />
            <div class="min-w-0 flex-1 rounded-2xl bg-surface-raised px-3 py-2">
              <p class="flex flex-wrap items-center gap-2 text-sm">
                <a [routerLink]="['/growers', comment.author]" class="font-semibold">{{ comment.author }}</a>
                @if (comment.byDiaryAuthor) {
                  <span class="rounded-full bg-accent/15 px-2 text-xs font-semibold text-accent">autor</span>
                }
                <time class="text-xs text-fg-muted" [attr.datetime]="comment.createdAt">{{ when(comment.createdAt) }}</time>
                @if (comment.underReview) {
                  <span class="rounded-full bg-warn/20 px-2 text-xs" title="Só você vê este comentário até a moderação revisar">em análise pela moderação</span>
                }
              </p>
              <p class="mt-1 whitespace-pre-line break-words">{{ comment.body }}</p>
              @if (auth.isLoggedIn()) {
                <div class="mt-1 flex gap-3 text-xs text-fg-muted">
                  @if (comment.deletable) {
                    <button type="button" class="inline-flex items-center gap-1" (click)="remove(comment)">
                      <app-icon name="trash" class="size-3.5" /> Apagar
                    </button>
                  } @else {
                    <app-report-button targetType="COMMENT" [targetId]="comment.id" (reported)="refresh()" />
                  }
                </div>
              }
            </div>
          </li>
        }
      </ul>
    }

    @if (auth.isLoggedIn()) {
      <form class="mt-4 flex items-end gap-2" (submit)="$event.preventDefault(); send()">
        <div class="flex-1">
          <label for="new-comment" class="sr-only">Escreva um comentário</label>
          <textarea
            id="new-comment"
            rows="2"
            maxlength="2000"
            class="wd-input min-h-12 py-2 text-base"
            placeholder="Escreva um comentário"
            [formControl]="body"
          ></textarea>
        </div>
        <button type="submit" class="wd-button px-3" aria-label="Enviar comentário" [disabled]="body.invalid || busy()">
          <app-icon name="send" class="size-5" />
        </button>
      </form>
    } @else {
      <p class="mt-4 text-sm text-fg-muted"><a routerLink="/entrar" class="font-semibold text-accent">Entre</a> para comentar.</p>
    }
    @if (message()) {
      <p role="status" class="mt-2 text-sm text-fg-muted">{{ message() }}</p>
    }
  `,
})
export class DiaryComments {
  private readonly api = inject(SocialApi);
  protected readonly auth = inject(AuthService);

  readonly diaryId = input.required<string>();
  readonly changed = output<void>();

  protected readonly body = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2000)] });
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly comments = toSignal(
    combineLatest([toObservable(this.diaryId), this.reload$]).pipe(
      switchMap(([id]) => this.api.comments(id).pipe(map((page) => page.items), catchError(() => of<Comment[]>([])))),
    ),
  );

  protected send(): void {
    const text = this.body.value.trim();
    if (!text) {
      return;
    }
    this.busy.set(true);
    this.api.comment(this.diaryId(), text).subscribe({
      next: () => {
        this.body.reset();
        this.message.set(null);
        this.refresh();
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false),
    });
  }

  protected remove(comment: Comment): void {
    if (!confirm('Apagar este comentário?')) {
      return;
    }
    this.api.deleteComment(this.diaryId(), comment.id).subscribe({
      next: () => this.refresh(),
      error: (error) => this.message.set(problemMessage(error)),
    });
  }

  protected when(instant: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(instant));
  }

  protected refresh(): void {
    this.reload$.next();
    this.changed.emit();
  }
}
