import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Comment } from '../../core/api/api.models';
import { problemMessage } from '../../core/api/problem';
import { SocialApi } from '../../core/api/social.api';
import { AuthService } from '../../core/auth/auth.service';
import { Avatar } from '../../shared/avatar';
import { CommentReactions, ReactionToggle, removeFromThread, updateInThread } from '../../shared/comment-reactions';
import { Icon } from '../../shared/icon';
import { ReportButton } from '../../shared/report-button';

/** Comentário que está sendo respondido; a resposta sempre fica debaixo do comentário raiz. */
interface ReplyTarget {
  readonly rootId: string;
  readonly commentId: string;
  readonly author: string;
}

/**
 * Conversa do diário: comentários com respostas (um nível) e reações anônimas. O texto é exibido por interpolação
 * (escapado pelo Angular), nunca como HTML.
 */
@Component({
  selector: 'app-diary-comments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, NgTemplateOutlet, Avatar, Icon, ReportButton, CommentReactions],
  template: `
    <h2 class="text-lg font-semibold">Comentários</h2>

    @if (comments(); as list) {
      @if (list.length === 0) {
        <p class="mt-2 text-sm text-fg-muted">Nenhum comentário ainda. Tire uma dúvida ou deixe uma dica.</p>
      }
      <ul class="mt-3 grid gap-4">
        @for (comment of list; track comment.id) {
          <li>
            <ng-container [ngTemplateOutlet]="item" [ngTemplateOutletContext]="{ $implicit: comment, rootId: comment.id, reply: false }" />
            @if (comment.replies?.length) {
              <ul class="mt-2 ml-4 grid gap-3 border-l-2 border-line pl-3 sm:ml-12" [attr.aria-label]="'Respostas ao comentário de ' + comment.author">
                @for (reply of comment.replies; track reply.id) {
                  <li>
                    <ng-container [ngTemplateOutlet]="item" [ngTemplateOutletContext]="{ $implicit: reply, rootId: comment.id, reply: true }" />
                  </li>
                }
              </ul>
            }
            @let hiddenReplies = (comment.replyCount ?? 0) - (comment.replies?.length ?? 0);
            @if (hiddenReplies > 0) {
              <button type="button" class="mt-1 ml-4 min-h-9 text-sm font-semibold text-accent sm:ml-12" [disabled]="loadingReplies() === comment.id" (click)="loadReplies(comment)">
                Ver mais {{ hiddenReplies }} {{ hiddenReplies === 1 ? 'resposta' : 'respostas' }}
              </button>
            }
            @if (replyingTo(); as target) {
              @if (target.rootId === comment.id) {
                <form class="mt-2 ml-4 grid gap-1 sm:ml-12" (submit)="$event.preventDefault(); sendReply()">
                  <div class="flex items-center justify-between gap-2 text-xs text-fg-muted">
                    <label [for]="'reply-' + comment.id">Respondendo a <strong class="font-semibold">{{ target.author }}</strong></label>
                    <button type="button" class="min-h-9 underline" (click)="cancelReply()">Cancelar</button>
                  </div>
                  <div class="flex items-end gap-2">
                    <textarea
                      [id]="'reply-' + comment.id"
                      rows="2"
                      maxlength="2000"
                      class="wd-input min-h-12 flex-1 py-2 text-base"
                      placeholder="Escreva uma resposta"
                      [formControl]="replyBody"
                    ></textarea>
                    <button type="submit" class="wd-button px-3" aria-label="Enviar resposta" [disabled]="replyBody.invalid || busy()">
                      <app-icon name="send" class="size-5" />
                    </button>
                  </div>
                </form>
              }
            }
          </li>
        }
      </ul>
    }

    <ng-template #item let-comment let-rootId="rootId" let-reply="reply">
      <div class="flex gap-3">
        <app-avatar class="text-sm" [class.size-9]="!reply" [class.size-7]="reply" [username]="comment.author" [hasAvatar]="comment.authorHasAvatar" />
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
          <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
            @if (!comment.underReview) {
              <app-comment-reactions
                [value]="comment.reactions"
                [author]="comment.author"
                [canReact]="auth.isLoggedIn()"
                [busy]="reacting() === comment.id"
                (toggled)="react(comment, $event)"
              />
            }
            @if (auth.isLoggedIn()) {
              @if (!comment.underReview) {
                <button type="button" class="inline-flex min-h-9 items-center gap-1" [attr.aria-label]="'Responder ' + comment.author" (click)="startReply(rootId, comment)">
                  <app-icon name="reply" class="size-3.5" /> Responder
                </button>
              }
              @if (comment.deletable) {
                <button type="button" class="inline-flex min-h-9 items-center gap-1" (click)="remove(comment)">
                  <app-icon name="trash" class="size-3.5" /> Apagar
                </button>
              } @else {
                <app-report-button targetType="COMMENT" [targetId]="comment.id" (reported)="refresh()" />
              }
            }
          </div>
        </div>
      </div>
    </ng-template>

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
      <p class="mt-4 text-sm text-fg-muted"><a routerLink="/entrar" class="font-semibold text-accent">Entre</a> para comentar e reagir.</p>
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
  protected readonly replyBody = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2000)] });
  protected readonly comments = signal<Comment[] | undefined>(undefined);
  protected readonly replyingTo = signal<ReplyTarget | null>(null);
  protected readonly reacting = signal<string | null>(null);
  protected readonly loadingReplies = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.diaryId();
      untracked(() => this.load(id));
    });
  }

  protected send(): void {
    const text = this.body.value.trim();
    if (text) {
      this.post(text, undefined, () => this.body.reset());
    }
  }

  protected startReply(rootId: string, comment: Comment): void {
    this.replyingTo.set({ rootId, commentId: comment.id, author: comment.author });
    this.replyBody.reset();
    setTimeout(() => document.getElementById('reply-' + rootId)?.focus());
  }

  protected cancelReply(): void {
    this.replyingTo.set(null);
    this.replyBody.reset();
  }

  protected sendReply(): void {
    const target = this.replyingTo();
    const text = this.replyBody.value.trim();
    if (target && text) {
      this.post(text, target.commentId, () => this.cancelReply());
    }
  }

  protected loadReplies(comment: Comment): void {
    this.loadingReplies.set(comment.id);
    this.api.replies(this.diaryId(), comment.id).subscribe({
      next: (page) => {
        this.comments.update((list) =>
          updateInThread(list ?? [], comment.id, (root) => ({ ...root, replies: page.items, replyCount: page.totalItems })),
        );
        this.loadingReplies.set(null);
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.loadingReplies.set(null);
      },
    });
  }

  protected react(comment: Comment, toggle: ReactionToggle): void {
    this.reacting.set(comment.id);
    const request = toggle.active
      ? this.api.unreactToComment(this.diaryId(), comment.id, toggle.type)
      : this.api.reactToComment(this.diaryId(), comment.id, toggle.type);
    request.subscribe({
      next: (reactions) => {
        this.comments.update((list) => updateInThread(list ?? [], comment.id, (c) => ({ ...c, reactions })));
        this.reacting.set(null);
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.reacting.set(null);
      },
    });
  }

  protected remove(comment: Comment): void {
    const question = comment.parentId || !comment.replyCount ? 'Apagar este comentário?' : 'Apagar este comentário e as respostas dele?';
    if (!confirm(question)) {
      return;
    }
    this.api.deleteComment(this.diaryId(), comment.id).subscribe({
      next: () => {
        this.comments.update((list) => removeFromThread(list ?? [], comment.id));
        this.changed.emit();
      },
      error: (error) => this.message.set(problemMessage(error)),
    });
  }

  protected when(instant: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(instant));
  }

  protected refresh(): void {
    this.load(this.diaryId());
    this.changed.emit();
  }

  private post(text: string, parentId: string | undefined, done: () => void): void {
    this.busy.set(true);
    this.api.comment(this.diaryId(), text, parentId).subscribe({
      next: () => {
        done();
        this.message.set(null);
        this.busy.set(false);
        this.refresh();
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }

  private load(id: string): void {
    this.api.comments(id).subscribe({
      next: (page) => this.comments.set(page.items),
      error: () => this.comments.set([]),
    });
  }
}
