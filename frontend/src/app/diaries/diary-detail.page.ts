import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { DiaryCard } from '../core/api/api.models';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { formatCompact } from '../shared/decimal';
import { Icon } from '../shared/icon';
import {
  ENVIRONMENT_LABEL,
  FLOWERING_LABEL,
  MEDIUM_LABEL,
  SEED_SEX_LABEL,
  STAGE_LABEL,
  STATUS_LABEL,
  formatDate,
} from '../shared/labels';
import { DiaryComments } from './detail/diary-comments';
import { DiaryOwnerPanel } from './detail/diary-owner-panel';
import { DiaryReactions } from './detail/diary-reactions';
import { DiaryTimeline } from './detail/diary-timeline';
import { ReviewForm } from './detail/review-form';

type Loaded = { diary: DiaryCard; error: null } | { diary: null; error: string };

@Component({
  selector: 'app-diary-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, DiaryComments, DiaryOwnerPanel, DiaryReactions, DiaryTimeline, ReviewForm],
  template: `
    @if (loaded(); as state) {
      @if (state.diary; as d) {
        <article class="grid gap-6">
          <header class="grid gap-2">
            <a routerLink="/diarios" class="text-sm text-fg-muted">← Diários</a>
            <div class="flex flex-wrap items-center gap-2 text-xs font-semibold">
              <span class="rounded-full bg-accent/15 px-2 py-0.5 text-accent">{{ stage[d.currentStage] }}</span>
              <span class="rounded-full bg-surface-raised px-2 py-0.5">Semana {{ d.currentWeek }} · dia {{ d.currentDay }}</span>
              <span class="rounded-full bg-surface-raised px-2 py-0.5">{{ status[d.status] }}</span>
              @if (d.visibility === 'PRIVATE') {
                <span class="inline-flex items-center gap-1 rounded-full bg-surface-raised px-2 py-0.5"><app-icon name="lock" class="size-3" /> Privado</span>
              }
            </div>
            <h1 class="text-2xl font-bold sm:text-3xl">{{ d.title }}</h1>
            <p class="text-fg-muted">
              {{ d.strainName }}{{ d.breeder ? ' · ' + d.breeder : '' }} · por
              <a [routerLink]="['/growers', d.author]" class="font-semibold text-fg">{{ d.author }}</a>
            </p>
          </header>

          <dl class="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <div class="wd-card p-3"><dt class="text-xs text-fg-muted">Meio</dt><dd class="font-semibold">{{ medium[d.medium] }}</dd></div>
            <div class="wd-card p-3"><dt class="text-xs text-fg-muted">Genética</dt><dd class="font-semibold">{{ flowering[d.floweringType] }} · {{ seedSex[d.seedSex] }}</dd></div>
            <div class="wd-card p-3"><dt class="text-xs text-fg-muted">Início</dt><dd class="font-semibold">{{ date(d.startedOn) }}</dd></div>
            @if (d.harvest; as h) {
              <div class="wd-card p-3"><dt class="text-xs text-fg-muted">Colheita</dt><dd class="font-semibold">{{ h.dryGrams }} g · {{ gramsPerPlant(h.dryGrams, h.plants) }} g/planta</dd></div>
            } @else if (d.endedOn) {
              <div class="wd-card p-3"><dt class="text-xs text-fg-muted">Encerrado</dt><dd class="font-semibold">{{ date(d.endedOn) }}</dd></div>
            }
          </dl>

          @if (d.setup; as setup) {
            <details class="wd-card">
              <summary class="cursor-pointer font-semibold">Setup: {{ setup.name }} ({{ environment[setup.environment] }})</summary>
              <dl class="mt-3 grid gap-1 text-sm sm:grid-cols-2">
                @if (setup.spec.tentWidthCm) {
                  <div><dt class="inline text-fg-muted">Tenda: </dt><dd class="inline">{{ setup.spec.tentWidthCm }}×{{ setup.spec.tentDepthCm ?? '?' }}×{{ setup.spec.tentHeightCm ?? '?' }} cm</dd></div>
                }
                @if (setup.spec.lighting) {
                  <div><dt class="inline text-fg-muted">Luz: </dt><dd class="inline">{{ setup.spec.lighting }}{{ setup.spec.lightWattage ? ' · ' + setup.spec.lightWattage + ' W' : '' }}</dd></div>
                }
                @if (setup.spec.nutrients) {
                  <div><dt class="inline text-fg-muted">Nutrientes: </dt><dd class="inline">{{ setup.spec.nutrients }}</dd></div>
                }
                @if (setup.spec.ventilation) {
                  <div><dt class="inline text-fg-muted">Ventilação: </dt><dd class="inline">{{ setup.spec.ventilation }}</dd></div>
                }
                @if (setup.spec.notes) {
                  <div class="sm:col-span-2"><dt class="inline text-fg-muted">Observações: </dt><dd class="inline whitespace-pre-line">{{ setup.spec.notes }}</dd></div>
                }
              </dl>
            </details>
          }

          @if (isOwner()) {
            <app-diary-owner-panel [diary]="d" (changed)="reload()" />
            @if (d.status === 'COMPLETED') {
              <app-review-form [diaryId]="d.id" [strain]="d.strainName" [breeder]="d.breeder" />
            }
          }

          <app-diary-reactions [diaryId]="d.id" />
          <app-diary-timeline [diaryId]="d.id" [isOwner]="isOwner()" [isPublic]="d.visibility === 'PUBLIC'" (changed)="reload()" />
          <section class="wd-card"><app-diary-comments [diaryId]="d.id" /></section>
        </article>
      } @else {
        <div class="wd-card text-center">
          <p class="font-medium">{{ state.error }}</p>
          <a routerLink="/diarios" class="mt-2 inline-block text-sm text-accent">Ver outros diários</a>
        </div>
      }
    } @else {
      <div class="grid gap-3" aria-busy="true">
        <div class="h-8 w-2/3 animate-pulse rounded-lg bg-surface"></div>
        <div class="h-40 animate-pulse rounded-2xl bg-surface"></div>
      </div>
    }
  `,
})
export class DiaryDetailPage {
  private readonly api = inject(DiariesApi);
  private readonly auth = inject(AuthService);

  /** Parâmetro de rota. */
  readonly id = input.required<string>();

  protected readonly stage = STAGE_LABEL;
  protected readonly status = STATUS_LABEL;
  protected readonly medium = MEDIUM_LABEL;
  protected readonly flowering = FLOWERING_LABEL;
  protected readonly seedSex = SEED_SEX_LABEL;
  protected readonly environment = ENVIRONMENT_LABEL;

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly loaded = toSignal(
    combineLatest([toObservable(this.id), this.reload$]).pipe(
      switchMap(([id]) =>
        this.api.get(id).pipe(
          map((diary): Loaded => ({ diary, error: null })),
          catchError((error) => of<Loaded>({ diary: null, error: problemMessage(error, 'Diário não encontrado.') })),
        ),
      ),
    ),
  );

  protected readonly isOwner = computed(() => {
    const state = this.loaded();
    return !!state?.diary && state.diary.author === this.auth.me()?.username;
  });

  protected reload(): void {
    this.reload$.next();
  }

  protected date(value: string): string {
    return formatDate(value);
  }

  protected gramsPerPlant(grams: number, plants: number): string {
    return formatCompact(grams / plants);
  }
}
