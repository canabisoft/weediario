import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { CatalogDbApi, CatalogItem, CatalogKind } from '../core/api/catalog-db.api';
import { ModerationApi, PendingCatalogItem } from '../core/api/moderation.api';
import { problemMessage } from '../core/api/problem';
import { timeAgo } from '../notifications/notification-text';
import { CatalogPicker } from '../shared/catalog-picker';
import { Icon } from '../shared/icon';
import { SOURCE_LABEL, categoryLabel } from '../shared/labels';

/** Singular de cada tipo, para as frases da fila. */
const NOUN: Record<CatalogKind, string> = { strains: 'strain', breeders: 'breeder', brands: 'marca', products: 'produto' };

/**
 * Fila de cadastros da comunidade ainda não verificados. Para cada um: verificar, juntar com um item parecido
 * (o nome vira apelido do principal) ou recusar (só se ninguém usa).
 */
@Component({
  selector: 'app-catalog-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, CatalogPicker],
  template: `
    @if (page(); as result) {
      <ul class="grid gap-3">
        @for (item of result.items; track item.id) {
          <li class="wd-card grid gap-3">
            <div class="flex flex-wrap items-start gap-2">
              <div class="min-w-0 flex-1">
                @if (detailLink(item); as target) {
                  <a [routerLink]="target" class="font-semibold hover:text-accent">{{ item.name }}</a>
                } @else {
                  <p class="font-semibold">{{ item.name }}</p>
                }
                <p class="text-sm text-fg-muted">
                  {{ item.parentName ?? '' }}{{ item.parentName && item.category ? ' · ' : '' }}{{ item.category ? category(item.category) : '' }}
                </p>
              </div>
              <p class="text-xs text-fg-muted">
                {{ item.usage }} {{ item.usage === 1 ? 'uso' : 'usos' }} · cadastrado {{ when(item.createdAt) }}
              </p>
            </div>

            @if (item.similar.length) {
              <div class="rounded-xl border border-warn/40 bg-warn/10 p-3 text-sm">
                <p class="font-semibold">Pode ser o mesmo que:</p>
                <ul class="mt-2 grid gap-2">
                  @for (candidate of item.similar; track candidate.id) {
                    <li class="flex flex-wrap items-center gap-2">
                      <span class="min-w-0 flex-1">
                        <span class="font-medium">{{ candidate.name }}</span>
                        <span class="text-xs text-fg-muted">
                          {{ candidate.parentName ? ' · ' + candidate.parentName : '' }} · {{ source[candidate.source] }}{{ candidate.status === 'VERIFIED' ? ' · verificado' : '' }}
                        </span>
                      </span>
                      <button type="button" class="wd-button min-h-9 bg-surface text-fg" [disabled]="busy() === item.id" (click)="merge(item, candidate)">
                        Juntar neste
                      </button>
                    </li>
                  }
                </ul>
              </div>
            }

            @if (choosing() === item.id) {
              <app-catalog-picker
                [kind]="kind()"
                [label]="'Juntar com outro ' + noun()"
                placeholder="Busque o item certo"
                [allowCreate]="false"
                [brandId]="kind() === 'products' ? item.parentId : undefined"
                [(value)]="otherTarget"
              />
              <div class="flex gap-2">
                <button type="button" class="wd-button" [disabled]="!otherTarget() || busy() === item.id" (click)="merge(item, otherTarget()!)">Juntar</button>
                <button type="button" class="wd-button bg-surface-raised text-fg" (click)="choosing.set(null)">Cancelar</button>
              </div>
            }

            <div class="flex flex-wrap gap-2">
              <button type="button" class="wd-button" [disabled]="busy() === item.id" (click)="verify(item)">
                <app-icon name="check" class="size-5" /> Verificar
              </button>
              @if (choosing() !== item.id) {
                <button type="button" class="wd-button bg-surface-raised text-fg" (click)="choose(item)">Juntar com outro…</button>
              }
              <button type="button" class="wd-button bg-surface-raised text-danger" [disabled]="busy() === item.id" (click)="reject(item)">
                <app-icon name="trash" class="size-5" /> Recusar
              </button>
            </div>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nenhum cadastro pendente aqui.</li>
        }
      </ul>
      @if (result.totalPages > 1) {
        <nav class="mt-4 flex items-center justify-between" aria-label="Páginas">
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page === 0" (click)="pageIndex.set(result.page - 1)">Anterior</button>
          <span class="text-sm text-fg-muted">{{ result.page + 1 }} de {{ result.totalPages }}</span>
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page + 1 >= result.totalPages" (click)="pageIndex.set(result.page + 1)">Próxima</button>
        </nav>
      }
    } @else {
      <div class="h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
    @if (message()) {
      <p role="status" class="mt-3 text-sm">{{ message() }}</p>
    }
  `,
})
export class CatalogQueue {
  private readonly moderation = inject(ModerationApi);
  private readonly catalog = inject(CatalogDbApi);

  readonly kind = input.required<CatalogKind>();
  /** Avisa a página para atualizar os contadores das abas. */
  readonly changed = output<void>();

  protected readonly source = SOURCE_LABEL;
  protected readonly noun = computed(() => NOUN[this.kind()]);
  protected readonly busy = signal<string | null>(null);
  protected readonly choosing = signal<string | null>(null);
  protected readonly otherTarget = signal<CatalogItem | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly pageIndex = signal(0);
  private readonly reload = signal(0);

  protected readonly page = toSignal(
    combineLatest([toObservable(this.kind), toObservable(this.pageIndex), toObservable(this.reload)]).pipe(
      switchMap(([kind, page]) =>
        this.moderation.pendingCatalog(kind, page).pipe(
          catchError(() => of<Page<PendingCatalogItem>>({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 })),
        ),
      ),
    ),
  );

  protected category(value: string): string {
    return categoryLabel(value);
  }

  protected when(instant: string): string {
    return timeAgo(instant);
  }

  protected detailLink(item: PendingCatalogItem): string[] | null {
    return this.kind() === 'strains' ? ['/strains', item.id] : this.kind() === 'breeders' ? ['/bancos', item.id] : null;
  }

  protected choose(item: PendingCatalogItem): void {
    this.otherTarget.set(null);
    this.choosing.set(item.id);
  }

  protected verify(item: PendingCatalogItem): void {
    this.act(item, this.catalog.verify(this.kind(), item.id), `"${item.name}" verificado.`);
  }

  protected merge(item: PendingCatalogItem, target: CatalogItem): void {
    this.act(item, this.catalog.merge(this.kind(), item.id, target.id), `"${item.name}" juntado em "${target.name}".`);
  }

  protected reject(item: PendingCatalogItem): void {
    this.act(item, this.catalog.reject(this.kind(), item.id), `"${item.name}" recusado.`);
  }

  private act(item: PendingCatalogItem, request: Observable<unknown>, done: string): void {
    this.busy.set(item.id);
    this.message.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(null);
        this.choosing.set(null);
        this.message.set(done);
        this.reload.update((n) => n + 1);
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(null);
        this.message.set(problemMessage(error));
      },
    });
  }
}
