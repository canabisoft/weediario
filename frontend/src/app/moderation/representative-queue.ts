import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, catchError, of, switchMap } from 'rxjs';
import { Page } from '../core/api/api.models';
import { BreederProgramApi, RepresentativeRequest } from '../core/api/breeder-program.api';
import { problemMessage } from '../core/api/problem';
import { timeAgo } from '../notifications/notification-text';
import { BREEDER_KIND_LABEL } from '../shared/labels';

/**
 * Pedidos para representar breeders, bancos ou independentes (pendentes primeiro), e representantes aprovados. Aprovar libera o cadastro
 * de sementes em teste; recusar ou revogar apaga o vínculo. A nota de prova só aparece aqui.
 */
@Component({
  selector: 'app-representative-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <p class="mb-3 text-sm text-fg-muted">
      Confira o vínculo antes de aprovar (site oficial, rede social do banco...). Representantes nunca são exibidos
      publicamente, e os cultivos e avaliações deles não contam no teste das sementes.
    </p>
    @if (page(); as result) {
      <ul class="grid gap-3">
        @for (item of result.items; track item.breederId + item.userId) {
          <li class="wd-card grid gap-2">
            <div class="flex flex-wrap items-start gap-2">
              <div class="min-w-0 flex-1">
                <a [routerLink]="['/bancos', item.breederId]" class="font-semibold hover:text-accent">{{ item.breeder }}</a>
                <p class="text-sm text-fg-muted">
                  por <a [routerLink]="['/growers', item.username]" class="underline">{{ item.username }}</a> · pedido {{ when(item.requestedAt) }}
                </p>
                <p class="text-xs text-fg-muted">
                  Diz representar: <strong class="text-fg">{{ kindLabel[item.claimedKind] }}</strong>
                  @if (item.publicCredit) {
                    · quer aparecer como criador(a)
                  }
                </p>
              </div>
              <span class="rounded-full px-2 py-0.5 text-xs font-semibold" [class]="item.status === 'APPROVED' ? 'bg-accent/15 text-accent' : 'bg-warn/15 text-warn'">
                {{ item.status === 'APPROVED' ? 'aprovado' : 'pendente' }}
              </span>
            </div>
            @if (item.note) {
              <p class="whitespace-pre-line break-words rounded-xl bg-surface-raised p-3 text-sm">{{ item.note }}</p>
            }
            <div class="flex flex-wrap gap-2">
              @if (item.status === 'PENDING') {
                <button type="button" class="wd-button" [disabled]="busy()" (click)="approve(item)">Aprovar</button>
                <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="reject(item, 'Recusar')">Recusar</button>
              } @else {
                <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="reject(item, 'Revogar')">Revogar</button>
              }
            </div>
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nenhum pedido de representação.</li>
        }
      </ul>
      @if (result.totalPages > 1) {
        <nav class="mt-4 flex items-center justify-center gap-3" aria-label="Paginação">
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page === 0" (click)="go(result.page - 1)">Anterior</button>
          <span class="text-sm text-fg-muted">{{ result.page + 1 }} / {{ result.totalPages }}</span>
          <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="result.page + 1 >= result.totalPages" (click)="go(result.page + 1)">Próxima</button>
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
export class RepresentativeQueue {
  protected readonly kindLabel = BREEDER_KIND_LABEL;
  private readonly api = inject(BreederProgramApi);

  /** Avisa o painel para atualizar os contadores das abas. */
  readonly changed = output<void>();

  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  private readonly request = signal({ page: 0, reload: 0 });

  protected readonly page = toSignal(
    toObservable(this.request).pipe(
      switchMap(({ page }) =>
        this.api.requests(page).pipe(catchError((error) => {
          this.message.set(problemMessage(error));
          return of({ items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 } as Page<RepresentativeRequest>);
        })),
      ),
    ),
  );

  protected when(iso: string): string {
    return timeAgo(iso);
  }

  protected go(page: number): void {
    this.request.update((current) => ({ ...current, page }));
  }

  protected approve(item: RepresentativeRequest): void {
    this.act(this.api.approve(item.breederId, item.userId), `${item.username} agora representa ${item.breeder}.`);
  }

  protected reject(item: RepresentativeRequest, verb: 'Recusar' | 'Revogar'): void {
    if (!confirm(`${verb} ${item.username} como representante de ${item.breeder}?`)) {
      return;
    }
    this.act(this.api.reject(item.breederId, item.userId), verb === 'Recusar' ? 'Pedido recusado.' : 'Representante revogado.');
  }

  private act(request: Observable<void>, done: string): void {
    this.busy.set(true);
    this.message.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.message.set(done);
        this.request.update((current) => ({ ...current, reload: current.reload + 1 }));
        this.changed.emit();
      },
      error: (error) => {
        this.busy.set(false);
        this.message.set(problemMessage(error));
      },
    });
  }
}
