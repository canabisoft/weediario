import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { RecipeComment, RecipeRating, RecipesApi } from '../core/api/recipes.api';
import { AuthService } from '../core/auth/auth.service';
import { formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { formatDate } from '../shared/labels';
import { StarInput } from '../shared/rating';

/**
 * Estrelas (a recomendação da comunidade, que ordena o ranking) e comentários de uma receita pública.
 */
import { ReportButton } from '../shared/report-button';

@Component({
  selector: 'app-recipe-feedback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, StarInput, ReportButton],
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
          <li class="rounded-lg bg-surface-raised p-3" [class.opacity-60]="comment.hidden">
            <p class="flex flex-wrap items-center gap-2 text-sm">
              <a [routerLink]="['/growers', comment.author]" class="font-semibold">{{ comment.author }}</a>
              <time class="text-xs text-fg-muted" [attr.datetime]="comment.createdAt">{{ date(comment.createdAt) }}</time>
              @if (comment.hidden) {
                <span class="rounded-full bg-warn/20 px-2 text-xs">em análise pela moderação</span>
              }
              <span class="ml-auto flex gap-3 text-xs text-fg-muted">
                @if (comment.canReport && !comment.hidden) {
                  <app-report-button targetType="RECIPE_COMMENT" [targetId]="comment.id" (reported)="loadComments(recipeId())" />
                }
                @if (comment.canDelete) {
                  <button type="button" (click)="remove(comment)">Apagar</button>
                }
              </span>
            </p>
            <p class="mt-1 whitespace-pre-line break-words">{{ comment.body }}</p>
          </li>
        } @empty {
          <li class="text-sm text-fg-muted">Nenhum comentário. Tire uma dúvida ou conte como foi com você.</li>
        }
      </ul>

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
  protected draft = '';

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

  protected remove(comment: RecipeComment): void {
    if (confirm('Apagar este comentário?')) {
      this.run(this.api.deleteComment(this.recipeId(), comment.id), () =>
        this.comments.update((list) => list.filter((c) => c.id !== comment.id)),
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
