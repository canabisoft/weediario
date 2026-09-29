import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { DiaryCard, GrowerProfile } from '../core/api/api.models';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { SocialApi } from '../core/api/social.api';
import { AuthService } from '../core/auth/auth.service';
import { DiaryCardComponent } from '../diaries/diary-card';
import { Avatar } from '../shared/avatar';
import { formatCompact, formatInteger } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { levelFor } from './grower-ranking';

type Loaded = { profile: GrowerProfile; error: null } | { profile: null; error: string };

/**
 * Perfil público: apelido, bio, nível, estatísticas dos diários públicos e contagem de seguidores
 * (a lista de quem segue não é exibida para ninguém).
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

        <dl class="mt-6 grid grid-cols-2 gap-2 sm:grid-cols-4">
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.diaries) }}</dd><dt class="text-xs text-fg-muted">Diários</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.likes) }}</dd><dt class="text-xs text-fg-muted">Curtidas</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ p.gramsPerPlant != null ? dec(p.gramsPerPlant) : '—' }}</dd><dt class="text-xs text-fg-muted">g/planta</dt></div>
          <div class="wd-card p-3 text-center"><dd class="text-xl font-bold tabular-nums">{{ int(p.followers) }}</dd><dt class="text-xs text-fg-muted">Seguidores</dt></div>
        </dl>

        <h2 class="mt-8 text-lg font-semibold">Diários públicos</h2>
        <ul class="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          @for (diary of diaries(); track diary.id) {
            <li><app-diary-card [diary]="diary" /></li>
          } @empty {
            <li class="wd-card text-center text-fg-muted sm:col-span-2 lg:col-span-3">Nenhum diário público ainda.</li>
          }
        </ul>
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

  protected readonly diaries = toSignal(
    toObservable(this.username).pipe(
      switchMap((author) =>
        this.diariesApi.feed({ author, size: 12 }).pipe(
          map((page) => page.items),
          catchError(() => of<DiaryCard[]>([])),
        ),
      ),
    ),
    { initialValue: [] },
  );

  protected level(diaries: number): string {
    return levelFor(diaries).name;
  }

  protected int(value: number): string {
    return formatInteger(value);
  }

  protected dec(value: number): string {
    return formatCompact(value);
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
