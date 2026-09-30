import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { CatalogKind, RankingKind } from '../core/api/catalog-db.api';
import { ModerationApi } from '../core/api/moderation.api';
import { CatalogQueue } from './catalog-queue';
import { CommentQueue } from './comment-queue';

type Tab = 'comentarios' | CatalogKind;

const TABS: readonly { id: Tab; label: string; count: RankingKind | 'COMMENTS' }[] = [
  { id: 'comentarios', label: 'Comentários', count: 'COMMENTS' },
  { id: 'strains', label: 'Strains', count: 'STRAINS' },
  { id: 'breeders', label: 'Breeders', count: 'BREEDERS' },
  { id: 'brands', label: 'Marcas', count: 'BRANDS' },
  { id: 'products', label: 'Produtos', count: 'PRODUCTS' },
];

/**
 * Painel da moderação: comentários denunciados e cadastros da comunidade à espera de revisão.
 * A rota é protegida por {@code moderatorGuard} e o backend exige o papel em cada chamada.
 */
@Component({
  selector: 'app-moderation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommentQueue, CatalogQueue],
  template: `
    <h1 class="text-2xl font-bold">Moderação</h1>
    <p class="text-fg-muted">Denúncias e cadastros da comunidade esperando revisão. Quem denunciou nunca aparece aqui.</p>

    <div class="mt-4 flex flex-wrap gap-2" role="tablist" aria-label="Filas da moderação">
      @for (option of tabs; track option.id) {
        <button
          type="button"
          role="tab"
          class="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-full px-4 text-sm font-semibold"
          [class]="tab() === option.id ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
          [attr.aria-selected]="tab() === option.id"
          (click)="select(option.id)"
        >
          {{ option.label }}
          @if (counts()[option.count]; as n) {
            <span class="rounded-full bg-canvas/80 px-2 text-xs tabular-nums text-fg">{{ n }}</span>
          }
        </button>
      }
    </div>

    <section class="mt-4" role="tabpanel">
      @if (tab() === 'comentarios') {
        <app-comment-queue (changed)="refreshCounts()" />
      } @else {
        <app-catalog-queue [kind]="$any(tab())" (changed)="refreshCounts()" />
      }
    </section>
  `,
})
export class ModerationPage {
  private readonly api = inject(ModerationApi);
  private readonly router = inject(Router);

  /** Parâmetro de consulta {@code ?aba=}. */
  readonly aba = input<string | undefined>();

  protected readonly tabs = TABS;
  protected readonly tab = computed<Tab>(() => TABS.find((t) => t.id === this.aba())?.id ?? 'comentarios');
  private readonly countsReload = signal(0);

  protected readonly counts = toSignal(
    toObservable(this.countsReload).pipe(
      switchMap(() =>
        combineLatest([
          this.api.commentCounts().pipe(catchError(() => of({ reported: 0, hidden: 0 }))),
          this.api.catalogCounts().pipe(catchError(() => of({} as Partial<Record<RankingKind, number>>))),
        ]),
      ),
      map(([comments, catalog]) => ({ COMMENTS: comments.reported, ...catalog }) as Record<string, number>),
    ),
    { initialValue: {} as Record<string, number> },
  );

  protected select(tab: Tab): void {
    this.router.navigate([], { queryParams: { aba: tab === 'comentarios' ? null : tab }, replaceUrl: true });
  }

  protected refreshCounts(): void {
    this.countsReload.update((n) => n + 1);
  }
}
