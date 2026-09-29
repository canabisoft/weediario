import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { DiaryCard, DiaryStatus, FeedSort, GrowMedium, GrowthStage } from '../core/api/api.models';
import { DiariesApi, FeedQuery } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { Icon } from '../shared/icon';
import { FEED_SORT_LABEL, MEDIUM_LABEL, STAGE_LABEL, STATUS_LABEL, options } from '../shared/labels';
import { DiaryCardComponent } from './diary-card';

interface FeedState {
  readonly items: DiaryCard[];
  readonly total: number;
  readonly loading: boolean;
  readonly error: string | null;
}

/**
 * Feed de diários da comunidade. Filtros ficam na URL (compartilháveis, voltar funciona); "Carregar mais"
 * busca a próxima página sem perder as anteriores.
 */
@Component({
  selector: 'app-diaries-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, DiaryCardComponent],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold">Diários</h1>
        <p class="text-fg-muted">Cultivos da comunidade, atualizados direto da tenda.</p>
      </div>
      @if (auth.isLoggedIn()) {
        <a routerLink="/perfil" class="text-sm font-semibold text-accent">Meus diários →</a>
      }
    </div>

    <div class="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
      <div class="col-span-2">
        <label class="wd-label" for="f-strain">Strain</label>
        <input
          id="f-strain"
          type="search"
          class="wd-input text-base"
          placeholder="Ex.: Gorilla Glue"
          maxlength="80"
          [value]="strain() ?? ''"
          (change)="update({ strain: $any($event.target).value || null })"
        />
      </div>
      <div>
        <label class="wd-label" for="f-stage">Fase</label>
        <select id="f-stage" class="wd-input text-base" (change)="update({ fase: $any($event.target).value || null })">
          <option value="">Todas</option>
          @for (o of stageOptions; track o.value) {
            <option [value]="o.value" [selected]="fase() === o.value">{{ o.label }}</option>
          }
        </select>
      </div>
      <div>
        <label class="wd-label" for="f-medium">Meio</label>
        <select id="f-medium" class="wd-input text-base" (change)="update({ meio: $any($event.target).value || null })">
          <option value="">Todos</option>
          @for (o of mediumOptions; track o.value) {
            <option [value]="o.value" [selected]="meio() === o.value">{{ o.label }}</option>
          }
        </select>
      </div>
      <div>
        <label class="wd-label" for="f-status">Status</label>
        <select id="f-status" class="wd-input text-base" (change)="update({ status: $any($event.target).value || null })">
          <option value="">Todos</option>
          @for (o of statusOptions; track o.value) {
            <option [value]="o.value" [selected]="status() === o.value">{{ o.label }}</option>
          }
        </select>
      </div>
      <div>
        <label class="wd-label" for="f-sort">Ordenar</label>
        <select id="f-sort" class="wd-input text-base" (change)="update({ ordem: $any($event.target).value })">
          @for (o of sortOptions; track o.value) {
            <option [value]="o.value" [selected]="sort() === o.value">{{ o.label }}</option>
          }
        </select>
      </div>
      @if (auth.isLoggedIn()) {
        <label class="col-span-2 flex min-h-12 items-center gap-2 self-end rounded-xl border border-line px-3 text-sm">
          <input
            type="checkbox"
            class="size-5"
            [checked]="following()"
            (change)="update({ seguindo: $any($event.target).checked ? 'sim' : null })"
          />
          Só de quem eu sigo
        </label>
      }
    </div>

    <p class="mt-4 text-sm text-fg-muted" aria-live="polite">
      @if (!state().loading || state().items.length) {
        {{ state().total }} {{ state().total === 1 ? 'diário' : 'diários' }}
      }
    </p>

    @if (state().error) {
      <p role="alert" class="wd-card mt-3 text-danger">{{ state().error }}</p>
    }

    <ul class="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      @for (diary of state().items; track diary.id) {
        <li><app-diary-card [diary]="diary" /></li>
      }
      @if (state().loading) {
        @for (placeholder of [1, 2, 3]; track placeholder) {
          <li class="aspect-[4/5] animate-pulse rounded-2xl bg-surface"></li>
        }
      }
    </ul>

    @if (!state().loading && !state().error && state().items.length === 0) {
      <div class="wd-card mt-3 text-center">
        <app-icon name="leaf" class="mx-auto size-10 text-accent/50" />
        <p class="mt-2 font-medium">Nenhum diário encontrado.</p>
        <p class="text-sm text-fg-muted">Tente outros filtros ou comece o seu.</p>
      </div>
    }

    @if (state().items.length < state().total && !state().loading) {
      <div class="mt-6 text-center">
        <button type="button" class="wd-button bg-surface-raised text-fg" (click)="loadMore()">Carregar mais</button>
      </div>
    }
  `,
})
export class DiariesPage {
  private readonly api = inject(DiariesApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  // Query params (via withComponentInputBinding).
  readonly strain = input<string | undefined>();
  readonly fase = input<GrowthStage | undefined>();
  readonly meio = input<GrowMedium | undefined>();
  readonly status = input<DiaryStatus | undefined>();
  readonly ordem = input<FeedSort | undefined>();
  readonly seguindo = input<string | undefined>();

  protected readonly stageOptions = options(STAGE_LABEL);
  protected readonly mediumOptions = options(MEDIUM_LABEL);
  protected readonly statusOptions = options(STATUS_LABEL);
  protected readonly sortOptions = options(FEED_SORT_LABEL);

  protected readonly sort = computed<FeedSort>(() => this.ordem() ?? 'RECENTLY_UPDATED');
  protected readonly following = computed(() => this.seguindo() === 'sim' && this.auth.isLoggedIn());
  private readonly page = signal(0);

  private readonly query = computed<FeedQuery>(() => ({
    strain: this.strain(),
    stage: this.fase(),
    medium: this.meio(),
    status: this.status(),
    sort: this.sort(),
    following: this.following() || undefined,
    size: 12,
  }));

  private readonly loaded: DiaryCard[] = [];
  private lastTotal = 0;

  protected readonly state = toSignal(
    toObservable(computed(() => ({ query: this.query(), page: this.page() }))).pipe(
      switchMap(({ query, page }) => {
        if (page === 0) {
          this.loaded.length = 0;
          this.lastTotal = 0;
        }
        return this.api.feed({ ...query, page }).pipe(
          map((result): FeedState => {
            this.loaded.push(...result.items);
            this.lastTotal = result.totalItems;
            return { items: [...this.loaded], total: result.totalItems, loading: false, error: null };
          }),
          startWith<FeedState>({ items: [...this.loaded], total: this.lastTotal, loading: true, error: null }),
          catchError((error) =>
            of<FeedState>({ items: [...this.loaded], total: this.lastTotal, loading: false, error: problemMessage(error) }),
          ),
        );
      }),
    ),
    { initialValue: { items: [], total: 0, loading: true, error: null } satisfies FeedState },
  );

  protected update(params: Record<string, string | null>): void {
    this.page.set(0);
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected loadMore(): void {
    this.page.update((page) => page + 1);
  }
}
