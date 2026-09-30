import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { BehaviorSubject, catchError, combineLatest, map, of, scan, switchMap } from 'rxjs';
import { DiaryPhotoRef, DiaryUpdate } from '../../core/api/api.models';
import { DiariesApi } from '../../core/api/diaries.api';
import { problemMessage } from '../../core/api/problem';
import { formatDecimal } from '../../shared/decimal';
import { DiaryPhoto } from '../../shared/diary-photo';
import { Icon } from '../../shared/icon';
import { ImageLightbox } from '../../shared/image-lightbox';
import { STAGE_LABEL, formatDate } from '../../shared/labels';

interface TimelineState {
  readonly items: DiaryUpdate[];
  readonly hasMore: boolean;
}

/**
 * Linha do tempo do cultivo, mais recente primeiro. Tocar numa foto abre o lightbox com as fotos do update.
 */
@Component({
  selector: 'app-diary-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DiaryPhoto, Icon, ImageLightbox],
  template: `
    <h2 class="text-lg font-semibold">Linha do tempo</h2>

    @if (timeline(); as t) {
      @if (t.items.length === 0) {
        <p class="mt-2 text-sm text-fg-muted">Ainda sem updates.</p>
      }
      <ol class="mt-3 grid gap-4 border-l-2 border-line pl-4">
        @for (update of t.items; track update.id) {
          <li class="relative">
            <span class="absolute -left-[1.4rem] top-1.5 size-3 rounded-full bg-accent ring-4 ring-canvas" aria-hidden="true"></span>
            <p class="flex flex-wrap items-baseline gap-x-2 text-sm">
              <span class="font-semibold">Semana {{ update.week }} · dia {{ update.day }}</span>
              <span class="rounded-full bg-surface-raised px-2 text-xs">{{ stage[update.stage] }}</span>
              <time class="text-xs text-fg-muted" [attr.datetime]="update.recordedOn">{{ date(update.recordedOn) }}</time>
              @if (isOwner()) {
                <button
                  type="button"
                  class="ml-auto inline-flex items-center gap-1 text-xs text-fg-muted"
                  (click)="remove(update)"
                >
                  <app-icon name="trash" class="size-3.5" /> Apagar
                </button>
              }
            </p>

            @if (update.photos.length) {
              <div class="mt-2 grid grid-cols-3 gap-1.5">
                @for (photo of update.photos; track photo.id; let i = $index) {
                  <button
                    type="button"
                    class="aspect-square overflow-hidden rounded-xl"
                    [attr.aria-label]="'Ampliar foto ' + (i + 1) + ' de ' + update.photos.length"
                    (click)="viewer.set({ photos: update.photos, index: i })"
                  >
                    <!-- Só a miniatura aqui; a versão em alta é baixada ao abrir o lightbox. -->
                    <app-diary-photo class="size-full" [url]="photo.thumbnailUrl" [isPublic]="isPublic()" alt="Foto do update" />
                  </button>
                }
              </div>
            }

            @if (update.note) {
              <p class="mt-2 whitespace-pre-line break-words">{{ update.note }}</p>
            }

            @if (hasMetrics(update)) {
              <dl class="mt-2 flex flex-wrap gap-2 text-xs">
                @if (update.temperatureCelsius != null) {
                  <div class="rounded-lg bg-surface-raised px-2 py-1"><dt class="inline text-fg-muted">Temp. </dt><dd class="inline font-semibold">{{ num(update.temperatureCelsius, 1) }} °C</dd></div>
                }
                @if (update.relativeHumidity != null) {
                  <div class="rounded-lg bg-surface-raised px-2 py-1"><dt class="inline text-fg-muted">Umidade </dt><dd class="inline font-semibold">{{ num(update.relativeHumidity, 0) }}%</dd></div>
                }
                @if (update.wateringPh != null) {
                  <div class="rounded-lg bg-surface-raised px-2 py-1"><dt class="inline text-fg-muted">pH </dt><dd class="inline font-semibold">{{ num(update.wateringPh, 1) }}</dd></div>
                }
                @if (update.plantHeightCm != null) {
                  <div class="rounded-lg bg-surface-raised px-2 py-1"><dt class="inline text-fg-muted">Altura </dt><dd class="inline font-semibold">{{ num(update.plantHeightCm, 0) }} cm</dd></div>
                }
              </dl>
            }
          </li>
        }
      </ol>
      @if (t.hasMore) {
        <button type="button" class="wd-button mt-4 w-full bg-surface-raised text-fg" (click)="more()">Updates anteriores</button>
      }
    }
    @if (error()) {
      <p role="alert" class="mt-2 text-sm text-danger">{{ error() }}</p>
    }

    @if (viewer(); as v) {
      <app-image-lightbox [photos]="v.photos" [startIndex]="v.index" [isPublic]="isPublic()" alt="Foto do update" (closed)="viewer.set(null)" />
    }
  `,
})
export class DiaryTimeline {
  private readonly api = inject(DiariesApi);

  readonly diaryId = input.required<string>();
  readonly isOwner = input(false);
  readonly isPublic = input(true);
  /** Emitido quando um update é apagado (o cabeçalho do diário muda de estágio). */
  readonly changed = output<void>();

  protected readonly stage = STAGE_LABEL;
  protected readonly viewer = signal<{ photos: DiaryPhotoRef[]; index: number } | null>(null);
  protected readonly error = signal<string | null>(null);
  private readonly reload$ = new BehaviorSubject<void>(undefined);
  private readonly page$ = new BehaviorSubject<number>(0);

  protected readonly timeline = toSignal(
    combineLatest([toObservable(this.diaryId), this.reload$]).pipe(
      switchMap(([id]) => {
        this.page$.next(0);
        return this.page$.pipe(
          switchMap((page) =>
            this.api.updates(id, page).pipe(
              map((result) => ({ page, result })),
              catchError((error) => {
                this.error.set(problemMessage(error));
                return of(null);
              }),
            ),
          ),
          scan<{ page: number; result: { items: DiaryUpdate[]; totalPages: number } } | null, TimelineState>(
            (state, loaded) =>
              loaded
                ? {
                    items: loaded.page === 0 ? loaded.result.items : [...state.items, ...loaded.result.items],
                    hasMore: loaded.page + 1 < loaded.result.totalPages,
                  }
                : state,
            { items: [], hasMore: false },
          ),
        );
      }),
    ),
  );

  protected more(): void {
    this.page$.next(this.page$.value + 1);
  }

  protected remove(update: DiaryUpdate): void {
    if (!confirm('Apagar este update? As fotos dele também serão apagadas definitivamente.')) {
      return;
    }
    this.api.deleteUpdate(this.diaryId(), update.id).subscribe({
      next: () => {
        this.reload$.next();
        this.changed.emit();
      },
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  protected hasMetrics(update: DiaryUpdate): boolean {
    return [update.temperatureCelsius, update.relativeHumidity, update.wateringPh, update.plantHeightCm].some(
      (value) => value != null,
    );
  }

  protected num(value: number, decimals: number): string {
    return formatDecimal(value, decimals);
  }

  protected date(value: string): string {
    return formatDate(value);
  }
}
