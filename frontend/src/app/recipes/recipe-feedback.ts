import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { RecipeComment, RecipeRating, RecipesApi } from '../core/api/recipes.api';
import { AuthService } from '../core/auth/auth.service';
import { CommentReactions, ReactionToggle, removeFromThread, updateInThread } from '../shared/comment-reactions';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { formatDate } from '../shared/labels';
import { StarInput } from '../shared/rating';
import { ReportButton } from '../shared/report-button';

/** Comentário que está sendo respondido; a resposta sempre fica debaixo do comentário raiz. */
interface ReplyTarget {
  readonly rootId: string;
  readonly commentId: string;
  readonly author: string;
}

/**
 * Estrelas (a recomendação da comunidade, que ordena o ranking) e comentários de uma receita pública, com respostas
 * em um nível e reações anônimas.
 */
@Component({
  selector: 'app-recipe-feedback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, NgTemplateOutlet, Icon, StarInput, ReportButton, CommentReactions],
  template: `
    <section class="wd-card mt-4" aria-labelledby="rating-title">
      <h2 id="rating-title" class="font-semibold">Avaliação da comunidade</h2>
      @if (rating(); as r) {
        <p class="mt-1 flex items-center gap-2">
          @if (r.count && r.average != null) {
            <span class="inline-flex items-center gap-1 text-2xl font-bold tabular-nums">
              <app-icon name="star" class="size-6 text-accent" [filled]="true" />{{ num(r.average) }}
            </span>
            <span class="text-sm text-fg-muted">{{ r.count }} {{ r.count === 1 ? 'avaliação' : 'avaliações' }}</span>
          } @else {
            <span class="text-sm text-fg-muted">Ainda sem avaliações.</span>
          }
        </p>
        @if (r.canRate) {
          <div class="mt-3">
            <app-star-input name="recipe-stars" [label]="r.mine ? 'Sua nota (toque para trocar)' : 'Já usou? Recomende com estrelas'"
                            [value]="r.mine ?? null" (valueChange)="rate($event)" />
            @if (r.mine) {
              <button type="button" class="mt-1 text-sm text-fg-muted underline" [disabled]="busy()" (click)="unrate()">Remover minha nota</button>
            }
          </div>
        } @else if (!auth.isLoggedIn()) {
          <p class="mt-2 text-sm"><a routerLink="/entrar" class="text-accent underline">Entre</a> para avaliar.</p>
        }
      }
    </section>

    <section class="wd-card mt-4" aria-labelledby="comments-title">
      <h2 id="comments-title" class="font-semibold">Comentários</h2>
      <ul class="mt-3 grid gap-3">
        @for (comment of comments(); track comment.id) {
          <li class="rounded-lg bg-surface-raised p-3">
            <ng-container [ngTemplateOutlet]="item" [ngTemplateOutletContext]="{ $implicit: comment, rootId: comment.id }" />
            @if (comment.replies?.length) {
              <ul class="mt-2 ml-2 grid gap-2 border-l-2 border-line pl-3" [attr.aria-label]="'Respostas ao comentário de ' + comment.author">
                @for (reply of comment.replies; track reply.id) {
                  <li>
                    <ng-container [ngTemplateOutlet]="item" [ngTemplateOutletContext]="{ $implicit: reply, rootId: comment.id }" />
                  </li>
                }
              </ul>
            }
            @let hiddenReplies = (comment.replyCount ?? 0) - (comment.replies?.length ?? 0);
            @if (hiddenReplies > 0) {
              <button type="button" class="mt-1 ml-2 min-h-9 text-sm font-semibold text-accent" [disabled]="busy()" (click)="loadReplies(comment)">
                Ver mais {{ hiddenReplies }} {{ hiddenReplies === 1 ? 'resposta' : 'respostas' }}
              </button>
            }
            @if (replyingTo(); as target) {
              @if (target.rootId === comment.id) {
                <form class="mt-2 ml-2 grid gap-1" (ngSubmit)="sendReply()">
                  <div class="flex items-center justify-between gap-2 text-xs text-fg-muted">
                    <label [for]="'recipe-reply-' + comment.id">Respondendo a <strong class="font-semibold">{{ target.author }}</strong></label>
                    <button type="button" class="min-h-9 underline" (click)="cancelReply()">Cancelar</button>
                  </div>
                  <div class="flex items-end gap-2">
                    <textarea [id]="'recipe-reply-' + comment.id" rows="2" maxlength="1000" class="wd-input flex-1 py-2 text-base"
                              placeholder="Escreva uma resposta" [(ngModel)]="replyDraft" name="reply"></textarea>
                    <button type="submit" class="wd-button px-3" aria-label="Enviar resposta" [disabled]="busy() || !replyDraft.trim()">
                      <app-icon name="send" class="size-5" />
                    </button>
                  </div>
                </form>
              }
            }
          </li>
        } @empty {
          <li class="text-sm text-fg-muted">Nenhum comentário. Tire uma dúvida ou conte como foi com você.</li>
        }
      </ul>

      <ng-template #item let-comment let-rootId="rootId">
        <div [class.opacity-60]="comment.hidden">
          <p class="flex flex-wrap items-center gap-2 text-sm">
            <a [routerLink]="['/growers', comment.author]" class="font-semibold">{{ comment.author }}</a>
            <time class="text-xs text-fg-muted" [attr.datetime]="comment.createdAt">{{ date(comment.createdAt) }}</time>
            @if (comment.hidden) {
              <span class="rounded-full bg-warn/20 px-2 text-xs">em análise pela moderação</span>
            }
          </p>
          <p class="mt-1 whitespace-pre-line break-words">{{ comment.body }}</p>
          <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
            @if (!comment.hidden) {
              <app-comment-reactions [value]="comment.reactions" [author]="comment.author" [canReact]="auth.isLoggedIn()"
                                     [busy]="reacting() === comment.id" (toggled)="react(comment, $event)" />
            }
            @if (auth.isLoggedIn() && !comment.hidden) {
              <button type="button" class="inline-flex min-h-9 items-center gap-1" [attr.aria-label]="'Responder ' + comment.author"
                      (click)="startReply(rootId, comment)">
                <app-icon name="reply" class="size-3.5" /> Responder
              </button>
            }
            @if (comment.canReport && !comment.hidden) {
              <app-report-button targetType="RECIPE_COMMENT" [targetId]="comment.id" (reported)="loadComments(recipeId())" />
            }
            @if (comment.canDelete) {
              <button type="button" class="min-h-9" (click)="remove(comment)">Apagar</button>
            }
          </div>
        </div>
      </ng-template>

      @if (auth.isLoggedIn()) {
        <form class="mt-3 grid gap-2" (ngSubmit)="send()">
          <label class="sr-only" for="recipe-comment">Comentário</label>
          <textarea id="recipe-comment" rows="3" maxlength="1000" class="wd-input py-2 text-base"
                    placeholder="Ex.: funcionou bem em coco? qual EC você usou na semana 5?" [(ngModel)]="draft" name="body"></textarea>
          <button type="submit" class="wd-button w-fit" [disabled]="busy() || !draft.trim()">
            <app-icon name="send" class="size-5" /> Comentar
          </button>
        </form>
      }
      @if (message()) {
        <p role="status" class="mt-2 text-sm text-fg-muted">{{ message() }}</p>
      }
    </section>
  `,
})
export class RecipeFeedback {
  private readonly api = inject(RecipesApi);
  protected readonly auth = inject(AuthService);

  readonly recipeId = input.required<string>();

  protected readonly rating = signal<RecipeRating | null>(null);
  protected readonly comments = signal<RecipeComment[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly replyingTo = signal<ReplyTarget | null>(null);
  protected readonly reacting = signal<string | null>(null);
  protected draft = '';
  protected replyDraft = '';

  constructor() {
    effect(() => {
      const id = this.recipeId();
      untracked(() => {
        this.api.rating(id).subscribe({ next: (rating) => this.rating.set(rating), error: () => this.rating.set(null) });
        this.loadComments(id);
      });
    });
  }

  protected rate(stars: number | null): void {
    if (stars) {
      this.run(this.api.rate(this.recipeId(), stars), (rating) => this.rating.set(rating));
    }
  }

  protected unrate(): void {
    this.run(this.api.removeRating(this.recipeId()), (rating) => this.rating.set(rating));
  }

  protected send(): void {
    const body = this.draft.trim();
    if (body) {
      this.run(this.api.comment(this.recipeId(), body), (comment) => {
        this.comments.update((list) => [...list, comment]);
        this.draft = '';
      });
    }
  }

  protected startReply(rootId: string, comment: RecipeComment): void {
    this.replyingTo.set({ rootId, commentId: comment.id, author: comment.author });
    this.replyDraft = '';
    setTimeout(() => document.getElementById('recipe-reply-' + rootId)?.focus());
  }

  protected cancelReply(): void {
    this.replyingTo.set(null);
    this.replyDraft = '';
  }

  /** A resposta volta do servidor já pendurada no comentário raiz; entra no fim da conversa dele. */
  protected sendReply(): void {
    const target = this.replyingTo();
    const body = this.replyDraft.trim();
    if (target && body) {
      this.run(this.api.comment(this.recipeId(), body, target.commentId), (reply) => {
        this.comments.update((list) =>
          updateInThread(list, target.rootId, (root) => ({
            ...root,
            replies: [...(root.replies ?? []), reply],
            replyCount: (root.replyCount ?? 0) + 1,
          })),
        );
        this.cancelReply();
      });
    }
  }

  protected loadReplies(comment: RecipeComment): void {
    this.run(this.api.replies(this.recipeId(), comment.id), (page) =>
      this.comments.update((list) =>
        updateInThread(list, comment.id, (root) => ({ ...root, replies: page.items, replyCount: page.totalItems })),
      ),
    );
  }

  protected react(comment: RecipeComment, toggle: ReactionToggle): void {
    this.reacting.set(comment.id);
    const request = toggle.active
      ? this.api.unreactToComment(this.recipeId(), comment.id, toggle.type)
      : this.api.reactToComment(this.recipeId(), comment.id, toggle.type);
    request.subscribe({
      next: (reactions) => {
        this.comments.update((list) => updateInThread(list, comment.id, (c) => ({ ...c, reactions })));
        this.reacting.set(null);
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.reacting.set(null);
      },
    });
  }

  protected remove(comment: RecipeComment): void {
    const question = comment.parentId || !comment.replyCount ? 'Apagar este comentário?' : 'Apagar este comentário e as respostas dele?';
    if (confirm(question)) {
      this.run(this.api.deleteComment(this.recipeId(), comment.id), () =>
        this.comments.update((list) => removeFromThread(list, comment.id)),
      );
    }
  }

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  protected date(value: string): string {
    return formatDate(value.slice(0, 10));
  }

  protected loadComments(id: string): void {
    this.api.comments(id).subscribe({ next: (page) => this.comments.set(page.items), error: () => this.comments.set([]) });
  }

  private run<T>(request: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.message.set(null);
    request.subscribe({
      next: (value) => {
        onSuccess(value);
        this.busy.set(false);
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
