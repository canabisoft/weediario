import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { ReactionSummary, ReactionType } from '../../core/api/api.models';
import { SocialApi } from '../../core/api/social.api';
import { AuthService } from '../../core/auth/auth.service';
import { REACTIONS } from '../../shared/labels';

/**
 * Reações anônimas: todos veem só as contagens; cada pessoa vê apenas as próprias reações (para poder desfazer).
 */
@Component({
  selector: 'app-diary-reactions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="flex flex-wrap items-center gap-2" role="group" aria-label="Reações">
      @for (reaction of reactions; track reaction.type) {
        @let active = summary().mine.includes(reaction.type);
        <button
          type="button"
          class="inline-flex min-h-11 items-center gap-2 rounded-full border px-4 text-sm font-semibold"
          [class]="active ? 'border-accent bg-accent/15 text-accent' : 'border-line text-fg'"
          [attr.aria-pressed]="active"
          [attr.aria-label]="reaction.label + ': ' + summary().counts[reaction.type]"
          [disabled]="busy() || !auth.isLoggedIn()"
          (click)="toggle(reaction.type, active)"
        >
          <span class="wd-emoji" aria-hidden="true">{{ reaction.emoji }}</span>
          <span class="tabular-nums">{{ summary().counts[reaction.type] }}</span>
        </button>
      }
      @if (!auth.isLoggedIn()) {
        <a routerLink="/entrar" class="text-sm text-fg-muted underline">Entre para reagir</a>
      }
    </div>
    <p class="mt-1 text-xs text-fg-muted">Reações são anônimas: ninguém vê quem reagiu, nem o autor do diário.</p>
  `,
})
export class DiaryReactions {
  private readonly api = inject(SocialApi);
  protected readonly auth = inject(AuthService);

  readonly diaryId = input.required<string>();

  protected readonly reactions = REACTIONS;
  protected readonly busy = signal(false);
  private readonly override = signal<ReactionSummary | null>(null);

  private readonly loaded = toSignal(
    toObservable(this.diaryId).pipe(
      switchMap((id) => {
        this.override.set(null);
        return this.api.reactions(id).pipe(catchError(() => of(EMPTY)));
      }),
    ),
    { initialValue: EMPTY },
  );

  protected summary(): ReactionSummary {
    return this.override() ?? this.loaded();
  }

  protected toggle(type: ReactionType, active: boolean): void {
    this.busy.set(true);
    const request = active ? this.api.unreact(this.diaryId(), type) : this.api.react(this.diaryId(), type);
    request.subscribe({
      next: (summary) => this.override.set(summary),
      complete: () => this.busy.set(false),
      error: () => this.busy.set(false),
    });
  }
}

const EMPTY: ReactionSummary = { counts: { FIRE: 0, HERB: 0, CLAP: 0 }, mine: [] };
