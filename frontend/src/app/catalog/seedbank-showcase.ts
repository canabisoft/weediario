import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, scan, switchMap } from 'rxjs';
import { BreederStrainCard, CatalogDbApi } from '../core/api/catalog-db.api';
import { DiariesApi } from '../core/api/diaries.api';
import { DiaryCardComponent } from '../diaries/diary-card';
import { formatCompact, formatDecimal } from '../shared/decimal';
import { DiaryPhoto } from '../shared/diary-photo';
import { Icon } from '../shared/icon';
import { DOMINANCE_LABEL, SPEED_LABEL } from '../shared/labels';

const PAGE_SIZE = 24;
const LATEST_DIARIES = 6;

interface StrainsState {
  readonly items: BreederStrainCard[];
  readonly total: number;
  readonly hasMore: boolean;
}

/**
 * Vitrine do banco de sementes: um card por strain que ele tem no catálogo e os cultivos mais recentes da
 * comunidade com sementes dele. Só diários públicos entram (contagens e fotos).
 */
@Component({
  selector: 'app-seedbank-showcase',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DiaryCardComponent, DiaryPhoto, Icon],
  template: `
    <section class="mt-6" aria-labelledby="bank-strains">
      <h2 id="bank-strains" class="text-lg font-semibold">
        Sementes do banco
        @if (strains(); as s) {
          <span class="text-sm font-normal text-fg-muted">· {{ s.total }}</span>
        }
      </h2>
      @if (strains(); as s) {
        @if (s.items.length) {
          <ul class="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            @for (strain of s.items; track strain.id) {
              <li>
                <a
                  [routerLink]="['/strains', strain.id]"
                  class="group block h-full overflow-hidden rounded-2xl border border-line bg-surface transition-colors hover:border-accent/50"
                >
                  <div class="relative aspect-square">
                    @if (strain.coverThumbnailUrl) {
                      <app-diary-photo class="size-full" [url]="strain.coverThumbnailUrl" [alt]="'Cultivo de ' + strain.name" />
                    } @else {
                      <div class="grid size-full place-items-center bg-surface-raised text-accent/40">
                        <app-icon name="leaf" class="size-12" [strokeWidth]="1.2" />
                      </div>
                    }
                    @if (strain.status === 'VERIFIED') {
                      <span class="absolute right-2 top-2 grid size-6 place-items-center rounded-full bg-canvas/85 text-accent" title="Verificada">
                        <app-icon name="check" class="size-3.5" />
                      </span>
                    }
                  </div>
                  <div class="p-3">
                    <p class="truncate font-semibold group-hover:text-accent">{{ strain.name }}</p>
                    <p class="truncate text-xs text-fg-muted">{{ traits(strain) || 'Sem informações ainda' }}</p>
                    <p class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
                      <span>{{ strain.diaries }} {{ strain.diaries === 1 ? 'cultivo' : 'cultivos' }}</span>
                      @if (strain.reviewCount && strain.overall != null) {
                        <span class="inline-flex items-center gap-0.5 font-semibold text-fg">
                          <app-icon name="star" class="size-3.5 text-accent" [filled]="true" />{{ num(strain.overall) }}
                        </span>
                      }
                      @if (strain.avgGramsPerPlant != null) {
                        <span>{{ compact(strain.avgGramsPerPlant) }} g/planta</span>
                      }
                    </p>
                  </div>
                </a>
              </li>
            }
          </ul>
          @if (s.hasMore) {
            <button type="button" class="wd-button mt-3 w-full bg-surface-raised text-fg" (click)="more()">Ver mais sementes</button>
          }
        } @else {
          <p class="wd-card mt-3 text-sm text-fg-muted">Nenhuma strain deste banco no catálogo ainda. Elas aparecem quando alguém cadastra um cultivo com sementes dele.</p>
        }
      } @else {
        <div class="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4" aria-busy="true">
          @for (i of [1, 2, 3, 4]; track i) {
            <div class="aspect-[3/4] animate-pulse rounded-2xl bg-surface"></div>
          }
        </div>
      }
    </section>

    <section class="mt-6" aria-labelledby="bank-diaries">
      <h2 id="bank-diaries" class="text-lg font-semibold">Últimos cultivos com sementes do banco</h2>
      @if (latest(); as diaries) {
        @if (diaries.length) {
          <ul class="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            @for (diary of diaries; track diary.id) {
              <li><app-diary-card [diary]="diary" /></li>
            }
          </ul>
        } @else {
          <p class="wd-card mt-3 text-sm text-fg-muted">Ainda não há cultivos públicos com sementes deste banco.</p>
        }
      } @else {
        <div class="mt-3 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
      }
    </section>
  `,
})
export class SeedbankShowcase {
  private readonly catalog = inject(CatalogDbApi);
  private readonly diaries = inject(DiariesApi);

  readonly breederId = input.required<string>();

  private readonly page = signal(0);

  protected readonly strains = toSignal(
    toObservable(computed(() => ({ id: this.breederId(), page: this.page() }))).pipe(
      switchMap(({ id, page }) =>
        this.catalog.breederStrains(id, page, PAGE_SIZE).pipe(
          map((result) => ({ page, result })),
          catchError(() => of({ page, result: null })),
        ),
      ),
      scan<{ page: number; result: { items: BreederStrainCard[]; totalItems: number; totalPages: number } | null }, StrainsState>(
        (state, { page, result }) =>
          result
            ? {
                items: page === 0 ? result.items : [...state.items, ...result.items],
                total: result.totalItems,
                hasMore: page + 1 < result.totalPages,
              }
            : state,
        { items: [], total: 0, hasMore: false },
      ),
    ),
  );

  protected readonly latest = toSignal(
    toObservable(this.breederId).pipe(
      switchMap((breederId) =>
        this.diaries.feed({ breederId, sort: 'NEWEST', size: LATEST_DIARIES }).pipe(
          map((result) => result.items),
          catchError(() => of([])),
        ),
      ),
    ),
  );

  protected more(): void {
    this.page.update((page) => page + 1);
  }

  protected traits(strain: BreederStrainCard): string {
    return [
      strain.dominance ? DOMINANCE_LABEL[strain.dominance] : null,
      strain.floweringType === 'AUTOFLOWER' ? 'automática' : strain.floweringType === 'PHOTOPERIOD' ? 'fotoperíodo' : null,
      strain.floweringSpeed ? SPEED_LABEL[strain.floweringSpeed] : null,
    ]
      .filter(Boolean)
      .join(' · ');
  }

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  protected compact(value: number): string {
    return formatCompact(value);
  }
}
