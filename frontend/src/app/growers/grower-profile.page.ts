import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, Subject, catchError, combineLatest, concatMap, map, of, scan, startWith, switchMap } from 'rxjs';
import { AuthoredReview, DiaryCard, GrowerProfile, Page, PublicSetup } from '../core/api/api.models';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { SocialApi } from '../core/api/social.api';
import { AuthService } from '../core/auth/auth.service';
import { DiaryCardComponent } from '../diaries/diary-card';
import { Avatar } from '../shared/avatar';
import { formatCompact, formatInteger } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { ENVIRONMENT_LABEL, MEDIUM_LABEL, categoryLabel, formatDate } from '../shared/labels';
import { levelFor, nextLevel } from './grower-ranking';

type Loaded = { profile: GrowerProfile; error: null } | { profile: null; error: string };

const DIARIES_PER_PAGE = 12;

function emptyPage<T>(): Page<T> {
  return { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };
}

/** Junta as páginas carregadas por "mostrar mais"; a página 0 recomeça a lista. */
function accumulate<T>(acc: Page<T>, next: Page<T>): Page<T> {
  return next.page === 0 ? next : { ...next, items: [...acc.items, ...next.items] };
}

/**
 * Perfil público: apelido, bio, nível, estatísticas dos diários públicos, setups já usados em diários
 * públicos, diários e reviews. Só entra o que já é público em outro lugar; a lista de seguidores nunca aparece.
 */
@Component({
  selector: 'app-grower-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Avatar, Icon, DiaryCardComponent],
  template: `
    @if (loaded(); as state) {
      @if (state.profile; as p) {
        <header class="flex flex-col items-center gap-3 text-center sm:flex-row sm:text-left">
          <app-avatar class="size-24 text-3xl" [username]="p.username" [hasAvatar]="p.hasAvatar" />
          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold">{{ p.username }}</h1>
            <span class="mt-1 inline-block rounded-full bg-accent/15 px-2 py-0.5 text-xs font-semibold text-accent">
              {{ level(p.diaries) }}
            </span>
            @if (p.bio) {
              <p class="mt-2 whitespace-pre-line text-fg-muted">{{ p.bio }}</p>
            }
          </div>
          @if (p.isViewer) {
            <a routerLink="/perfil" class="wd-button bg-surface-raised text-fg"><app-icon name="edit" class="size-5" /> Editar perfil</a>
          } @else if (auth.isLoggedIn()) {
            <button type="button" class="wd-button" [class]="p.followedByViewer ? 'bg-surface-raised text-fg' : ''" [disabled]="busy()" (click)="toggleFollow(p)">
              <app-icon [name]="p.followedByViewer ? 'check' : 'userPlus'" class="size-5" />
              {{ p.followedByViewer ? 'Seguindo' : 'Seguir' }}
            </button>
          }
        </header>

        @if (progress(); as next) {
          <div class="wd-card mt-4 p-3">
            <p class="flex justify-between text-sm">
              <span>Próximo nível: <strong>{{ next.next.name }}</strong></span>
              <span class="text-fg-muted">{{ next.missing === 1 ? 'falta 1 diário público' : 'faltam ' + next.missing + ' diários públicos' }}</span>
            </p>
            <div class="mt-2 h-2 overflow-hidden rounded-full bg-surface-raised" role="progressbar"
                 [attr.aria-valuenow]="percent(next.progress)" aria-valuemin="0" aria-valuemax="100"
                 [attr.aria-label]="'Progresso até ' + next.next.name">
              <div class="h-full rounded-full bg-accent" [style.width.%]="percent(next.progress)"></div>
            </div>
          </div>
        }

        <dl class="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.diaries) }}</dd><dt class="text-xs text-fg-muted">Diários</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.harvests) }}</dd><dt class="text-xs text-fg-muted">Colheitas</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ p.gramsPerPlant != null ? dec(p.gramsPerPlant) : '—' }}</dd><dt class="text-xs text-fg-muted">g/planta (média)</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.likes) }}</dd><dt class="text-xs text-fg-muted">Curtidas</dt></div>
          <div class="wd-card col-span-2 p-3 text-center sm:col-span-1"><dd class="text-xl font-bold tabular-nums">{{ int(p.followers) }}</dd><dt class="text-xs text-fg-muted">Seguidores</dt></div>
        </dl>

        <h2 class="mt-8 text-lg font-semibold">Diários públicos</h2>
        <ul class="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (diary of diaries().items; track diary.id) {
            <li><app-diary-card [diary]="diary" /></li>
          } @empty {
            <li class="wd-card text-center text-fg-muted sm:col-span-2 lg:col-span-3">Nenhum diário público ainda.</li>
          }
        </ul>
        @if (diaries().page + 1 < diaries().totalPages) {
          <button type="button" class="wd-button mt-3 bg-surface-raised text-fg" (click)="moreDiaries$.next(diaries().page + 1)">
            Mais diários
          </button>
        }

        @if (setups().length) {
          <h2 class="mt-8 text-lg font-semibold">Setups</h2>
          <ul class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (item of setups(); track $index) {
              <li class="wd-card grid gap-2">
                <p class="flex items-start justify-between gap-2">
                  <span class="font-semibold">{{ item.setup.name ?? 'Setup' }}</span>
                  <span class="shrink-0 text-xs text-fg-muted">{{ item.diaries === 1 ? '1 diário' : item.diaries + ' diários' }}</span>
                </p>
                <p class="text-sm text-fg-muted">{{ summary(item) }}</p>
                @if (item.setup.products?.length) {
                  <ul class="flex flex-wrap gap-1" aria-label="Produtos">
                    @for (product of item.setup.products; track product.id) {
                      <li class="rounded-full bg-surface-raised px-2 py-0.5 text-xs" [title]="category(product.category)">
                        {{ product.brand }} · {{ product.name }}
                      </li>
                    }
                  </ul>
                }
                <p class="text-xs text-fg-muted">Último cultivo começou em {{ date(item.lastStartedOn) }}</p>
              </li>
            }
          </ul>
        }

        <h2 class="mt-8 text-lg font-semibold">Reviews de strains</h2>
        @if (reviews(); as page) {
          <ul class="mt-3 grid gap-3">
            @for (review of page.items; track review.id) {
              <li class="wd-card grid gap-1">
                <p class="flex flex-wrap items-center gap-2">
                  <a [routerLink]="['/strains', review.strainId]" class="font-semibold hover:text-accent">{{ review.strain }}</a>
                  @if (review.breeder) {
                    <span class="text-sm text-fg-muted">{{ review.breeder }}</span>
                  }
                  <span class="ml-auto inline-flex items-center gap-1 text-sm font-semibold text-accent" [attr.aria-label]="'Nota ' + dec(review.overall) + ' de 5'">
                    <app-icon name="star" class="size-4" [filled]="true" />{{ dec(review.overall) }}
                  </span>
                </p>
                <p class="line-clamp-3 whitespace-pre-line text-sm">{{ review.report }}</p>
                <p class="flex gap-3 text-xs text-fg-muted">
                  <time [attr.datetime]="review.createdAt">{{ date(review.createdAt) }}</time>
                  @if (review.diaryId) {
                    <a [routerLink]="['/diarios', review.diaryId]" class="text-accent">Ver o cultivo</a>
                  }
                </p>
              </li>
            } @empty {
              <li class="wd-card text-center text-fg-muted">Nenhum review publicado ainda.</li>
            }
          </ul>
          @if (page.page + 1 < page.totalPages) {
            <button type="button" class="wd-button mt-3 bg-surface-raised text-fg" (click)="moreReviews$.next(page.page + 1)">Mais reviews</button>
          }
        }

        @if (error()) {
          <p role="alert" class="mt-3 text-sm text-danger">{{ error() }}</p>
        }
      } @else {
        <p class="wd-card text-center">{{ state.error }}</p>
      }
    } @else {
      <div class="h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class GrowerProfilePage {
  private readonly social = inject(SocialApi);
  private readonly diariesApi = inject(DiariesApi);
  protected readonly auth = inject(AuthService);

  /** Parâmetro de rota. */
  readonly username = input.required<string>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly loaded = toSignal(
    combineLatest([toObservable(this.username), this.reload$]).pipe(
      switchMap(([username]) =>
        this.social.profile(username).pipe(
          map((profile): Loaded => ({ profile, error: null })),
          catchError((error) => of<Loaded>({ profile: null, error: problemMessage(error, 'Grower não encontrado.') })),
        ),
      ),
    ),
  );

  protected readonly progress = computed(() => {
    const profile = this.loaded()?.profile;
    return profile ? nextLevel(profile.diaries) : null;
  });

  protected readonly moreDiaries$ = new Subject<number>();
  protected readonly moreReviews$ = new Subject<number>();

  protected readonly diaries = toSignal(
    toObservable(this.username).pipe(
      switchMap((author) =>
        this.moreDiaries$.pipe(
          startWith(0),
          concatMap((page) =>
            this.diariesApi.feed({ author, page, size: DIARIES_PER_PAGE }).pipe(catchError(() => of(emptyPage<DiaryCard>()))),
          ),
          scan(accumulate<DiaryCard>, emptyPage<DiaryCard>()),
        ),
      ),
    ),
    { initialValue: emptyPage<DiaryCard>() },
  );

  protected readonly setups = toSignal(
    toObservable(this.username).pipe(
      switchMap((username) => this.social.publicSetups(username).pipe(catchError(() => of<PublicSetup[]>([])))),
    ),
    { initialValue: [] },
  );

  /** "Mais reviews" acrescenta a próxima página embaixo; trocar de grower recomeça. */
  protected readonly reviews = toSignal(
    toObservable(this.username).pipe(
      switchMap((username) =>
        this.moreReviews$.pipe(
          startWith(0),
          concatMap((page) =>
            this.social.reviewsBy(username, page).pipe(catchError(() => of(emptyPage<AuthoredReview>()))),
          ),
          scan(accumulate<AuthoredReview>, emptyPage<AuthoredReview>()),
        ),
      ),
    ),
  );

  protected level(diaries: number): string {
    return levelFor(diaries).name;
  }

  protected percent(progress: number): number {
    return Math.round(progress * 100);
  }

  protected int(value: number): string {
    return formatInteger(value);
  }

  protected dec(value: number): string {
    return formatCompact(value);
  }

  protected date(value: string): string {
    return formatDate(value);
  }

  protected category(value: string): string {
    return categoryLabel(value);
  }

  /** "Indoor · 80×80×160 cm · LED 240 W · Solo vivo". */
  protected summary(item: PublicSetup): string {
    const { environment, spec } = item.setup;
    const parts: string[] = [];
    if (environment) {
      parts.push(ENVIRONMENT_LABEL[environment]);
    }
    if (spec?.tentWidthCm) {
      parts.push(`${spec.tentWidthCm}×${spec.tentDepthCm ?? '?'}×${spec.tentHeightCm ?? '?'} cm`);
    }
    if (spec?.lighting) {
      parts.push(spec.lightWattage ? `${spec.lighting} ${spec.lightWattage} W` : spec.lighting);
    }
    if (spec?.medium) {
      parts.push(MEDIUM_LABEL[spec.medium]);
    }
    return parts.join(' · ');
  }

  protected toggleFollow(profile: GrowerProfile): void {
    this.busy.set(true);
    const request = profile.followedByViewer
      ? this.social.unfollow(profile.username)
      : this.social.follow(profile.username);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.reload$.next();
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }
}
