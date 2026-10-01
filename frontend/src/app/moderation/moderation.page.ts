import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { CatalogKind, RankingKind } from '../core/api/catalog-db.api';
import { ModerationApi } from '../core/api/moderation.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { saveFile } from '../treatment/treatment-text';
import { CatalogQueue } from './catalog-queue';
import { ContentQueue } from './content-queue';

type Tab = 'denuncias' | CatalogKind | 'contas';

const TABS: readonly { id: Tab; label: string; count?: RankingKind | 'REPORTS' }[] = [
  { id: 'denuncias', label: 'Denúncias e retidos', count: 'REPORTS' },
  { id: 'strains', label: 'Strains', count: 'STRAINS' },
  { id: 'breeders', label: 'Breeders', count: 'BREEDERS' },
  { id: 'brands', label: 'Marcas', count: 'BRANDS' },
  { id: 'products', label: 'Produtos', count: 'PRODUCTS' },
  { id: 'contas', label: 'Contas e log' },
];

/**
 * Painel da moderação: conteúdo denunciado ou retido pelo filtro anti-comércio, cadastros da comunidade no
 * catálogo e contas suspensas. A rota é protegida por {@code moderatorGuard} e o backend exige o papel em cada
 * chamada. Toda decisão vai para o log de moderação (sem IP e sem quem denunciou).
 */
@Component({
  selector: 'app-moderation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ContentQueue, CatalogQueue],
  template: `
    <h1 class="text-2xl font-bold">Moderação</h1>
    <p class="text-fg-muted">
      Denúncias, conteúdo retido pelo filtro anti-comércio e cadastros da comunidade. Quem denunciou nunca aparece aqui.
    </p>

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
          @if (option.count && counts()[option.count]; as n) {
            <span class="rounded-full bg-canvas/80 px-2 text-xs tabular-nums text-fg">{{ n }}</span>
          }
        </button>
      }
    </div>

    <section class="mt-4" role="tabpanel">
      @switch (tab()) {
        @case ('denuncias') {
          <app-content-queue (changed)="refreshCounts()" />
        }
        @case ('contas') {
          <div class="grid gap-4">
            <form class="wd-card grid gap-2" (submit)="$event.preventDefault(); reinstate()" aria-labelledby="reinstate-title">
              <h2 id="reinstate-title" class="font-semibold">Reativar conta suspensa ou banida</h2>
              <p class="text-sm text-fg-muted">Use quando um recurso for aceito ou a decisão tiver sido um engano. Fica registrado no log.</p>
              <div class="grid gap-2 sm:grid-cols-[1fr_2fr_auto]">
                <input class="wd-input" maxlength="30" placeholder="Apelido" aria-label="Apelido" [value]="username()"
                       (input)="username.set($any($event.target).value)" />
                <input class="wd-input" maxlength="500" placeholder="Motivo (opcional)" aria-label="Motivo da reativação"
                       [value]="note()" (input)="note.set($any($event.target).value)" />
                <button type="submit" class="wd-button" [disabled]="!username().trim() || busy()">Reativar</button>
              </div>
            </form>

            @if (auth.isAdmin()) {
              <form class="wd-card grid gap-2" (submit)="$event.preventDefault(); exportLog()" aria-labelledby="log-title">
                <h2 id="log-title" class="font-semibold">Log de moderação (CSV)</h2>
                <p class="text-sm text-fg-muted">
                  Para atender ordem judicial ou responder a questionamentos: data, conteúdo, conta, moderador, ação, motivo e
                  o hash do conteúdo removido. Sem IP e sem quem denunciou. Guardado por 5 anos.
                </p>
                <div class="grid gap-2 sm:grid-cols-4">
                  <input class="wd-input" maxlength="30" placeholder="Apelido (opcional)" aria-label="Apelido da conta"
                         [value]="logUser()" (input)="logUser.set($any($event.target).value)" />
                  <input type="date" class="wd-input text-base" aria-label="De" [value]="logFrom()" (input)="logFrom.set($any($event.target).value)" />
                  <input type="date" class="wd-input text-base" aria-label="Até" [value]="logTo()" (input)="logTo.set($any($event.target).value)" />
                  <button type="submit" class="wd-button" [disabled]="busy()">Baixar CSV</button>
                </div>
              </form>
            }
          </div>
        }
        @default {
          <app-catalog-queue [kind]="$any(tab())" (changed)="refreshCounts()" />
        }
      }
      @if (message()) {
        <p role="status" class="mt-3 text-sm">{{ message() }}</p>
      }
    </section>
  `,
})
export class ModerationPage {
  private readonly api = inject(ModerationApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Parâmetro de consulta {@code ?aba=}. */
  readonly aba = input<string | undefined>();

  protected readonly tabs = TABS;
  protected readonly tab = computed<Tab>(() => TABS.find((t) => t.id === this.aba())?.id ?? 'denuncias');
  private readonly countsReload = signal(0);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly username = signal('');
  protected readonly note = signal('');
  protected readonly logUser = signal('');
  protected readonly logFrom = signal('');
  protected readonly logTo = signal('');

  protected readonly counts = toSignal(
    toObservable(this.countsReload).pipe(
      switchMap(() =>
        combineLatest([
          this.api.queueCounts().pipe(catchError(() => of({ pending: 0, held: 0, byType: {} }))),
          this.api.catalogCounts().pipe(catchError(() => of({} as Partial<Record<RankingKind, number>>))),
        ]),
      ),
      map(([queue, catalog]) => ({ REPORTS: queue.pending, ...catalog }) as Record<string, number>),
    ),
    { initialValue: {} as Record<string, number> },
  );

  protected select(tab: Tab): void {
    this.message.set(null);
    this.router.navigate([], { queryParams: { aba: tab === 'denuncias' ? null : tab }, replaceUrl: true });
  }

  protected refreshCounts(): void {
    this.countsReload.update((n) => n + 1);
  }

  protected reinstate(): void {
    this.busy.set(true);
    this.api.reinstate(this.username().trim(), this.note().trim() || undefined).subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set(`Conta ${this.username().trim()} reativada.`);
        this.username.set('');
        this.note.set('');
      },
      error: (error) => {
        this.busy.set(false);
        this.message.set(problemMessage(error));
      },
    });
  }

  protected exportLog(): void {
    this.busy.set(true);
    this.api.log(this.logUser().trim() || undefined, this.logFrom() || undefined, this.logTo() || undefined).subscribe({
      next: (blob) => {
        this.busy.set(false);
        saveFile(blob, 'log-moderacao.csv');
      },
      error: (error) => {
        this.busy.set(false);
        this.message.set(problemMessage(error));
      },
    });
  }
}
