import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ReactionSummary, ReactionType } from '../core/api/api.models';
import { REACTIONS } from './labels';

export const NO_REACTIONS: ReactionSummary = { counts: { FIRE: 0, HERB: 0, CLAP: 0 }, mine: [] };

/** Pedido de troca de uma reação: {@code active} diz se a pessoa já tinha reagido (então é para desfazer). */
export interface ReactionToggle {
  readonly type: ReactionType;
  readonly active: boolean;
}

/**
 * Reações anônimas de um comentário (🔥 🌿 👏). Mostra só contagens; cada pessoa vê destacadas apenas as próprias
 * reações. Sem login, aparecem só as reações que já existem, sem botão.
 */
@Component({
  selector: 'app-comment-reactions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span class="inline-flex flex-wrap items-center gap-1" role="group" [attr.aria-label]="'Reações ao comentário de ' + author()">
      @for (reaction of reactions; track reaction.type) {
        @let active = summary().mine.includes(reaction.type);
        @let count = summary().counts[reaction.type] ?? 0;
        @if (canReact()) {
          <button
            type="button"
            class="inline-flex min-h-9 items-center gap-1 rounded-full border px-2.5 text-xs font-semibold"
            [class]="active ? 'border-accent bg-accent/15 text-accent' : 'border-line text-fg-muted'"
            [attr.aria-pressed]="active"
            [attr.aria-label]="reaction.label + ': ' + count"
            [disabled]="busy()"
            (click)="toggled.emit({ type: reaction.type, active })"
          >
            <span class="wd-emoji" aria-hidden="true">{{ reaction.emoji }}</span>
            <span class="tabular-nums">{{ count }}</span>
          </button>
        } @else if (count > 0) {
          <span class="inline-flex items-center gap-1 text-xs text-fg-muted" [attr.aria-label]="reaction.label + ': ' + count">
            <span class="wd-emoji" aria-hidden="true">{{ reaction.emoji }}</span>
            <span class="tabular-nums" aria-hidden="true">{{ count }}</span>
          </span>
        }
      }
    </span>
  `,
})
export class CommentReactions {
  readonly value = input<ReactionSummary | undefined>(undefined);
  readonly author = input('');
  readonly canReact = input(false);
  readonly busy = input(false);
  readonly toggled = output<ReactionToggle>();

  protected readonly reactions = REACTIONS;
  protected readonly summary = computed(() => this.value() ?? NO_REACTIONS);
}

/** O mínimo que um comentário com respostas tem (diário ou receita). */
export interface ThreadComment<T> {
  readonly id: string;
  readonly replyCount?: number;
  readonly replies?: T[];
}

/** Aplica {@code change} ao comentário (raiz ou resposta) com o id dado. */
export function updateInThread<T extends ThreadComment<T>>(list: T[], id: string, change: (comment: T) => T): T[] {
  return list.map((comment) => {
    if (comment.id === id) {
      return change(comment);
    }
    if (comment.replies?.some((reply) => reply.id === id)) {
      return { ...comment, replies: comment.replies.map((reply) => (reply.id === id ? change(reply) : reply)) };
    }
    return comment;
  });
}

/** Tira um comentário da conversa: o raiz sai com as respostas; a resposta sai e o contador diminui. */
export function removeFromThread<T extends ThreadComment<T>>(list: T[], id: string): T[] {
  return list
    .filter((comment) => comment.id !== id)
    .map((comment) =>
      comment.replies?.some((reply) => reply.id === id)
        ? {
            ...comment,
            replies: comment.replies.filter((reply) => reply.id !== id),
            replyCount: Math.max((comment.replyCount ?? 1) - 1, 0),
          }
        : comment,
    );
}
