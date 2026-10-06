import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, of, switchMap } from 'rxjs';
import { DiaryCard } from '../core/api/api.models';
import { CatalogApi, OwnGrow } from '../core/api/catalog.api';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { ReviewForm } from '../diaries/detail/review-form';
import { Icon } from '../shared/icon';

/**
 * "Avaliar esta strain" na página dela. A nota continua vindo de um cultivo real e concluído da própria pessoa
 * (é o que dá peso às médias e ao ranking), mas o caminho fica aqui: o formulário abre na própria página e, para
 * quem ainda não cultivou, o painel explica o que falta e leva direto ao diário novo com a strain escolhida.
 */
@Component({
  selector: 'app-strain-review-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, ReviewForm],
  template: `
    <section id="avaliar" class="wd-card mt-4 grid scroll-mt-24 gap-3 border-accent/40" aria-labelledby="rate-title">
      <h2 id="rate-title" class="flex items-center gap-2 text-lg font-semibold">
        <app-icon name="star" class="size-5 text-accent" [filled]="true" /> Avaliar {{ strainName() }}
      </h2>

      @if (!auth.isLoggedIn()) {
        <p class="text-sm text-fg-muted">
          Cultivou esta strain? <a routerLink="/entrar" [queryParams]="{ voltar: '/strains/' + strainId() }" class="text-accent underline">Entre</a>
          para dar a sua nota. Cada avaliação vale pontos no ranking de growers.
        </p>
      } @else if (sent()) {
        <p class="font-semibold text-accent" role="status">Obrigado! Sua avaliação foi publicada e já conta no ranking.</p>
      } @else if (grows(); as list) {
        @if (pending(list); as ready) {
          @if (form(); as diary) {
            <app-review-form [diaryId]="diary.id" [strain]="diary.strainName" [breeder]="diary.breeder"
                             [products]="diary.setup?.products ?? []" (submitted)="onSubmitted()" />
          } @else {
            <p class="text-sm">
              Você concluiu {{ ready.length === 1 ? 'um cultivo' : ready.length + ' cultivos' }} desta strain. Conte como foi:
              a nota entra na média da comunidade e conta pontos no ranking de growers.
            </p>
            @if (ready.length > 1) {
              <div>
                <label class="wd-label" for="rate-diary">Qual cultivo?</label>
                <select id="rate-diary" class="wd-input" [ngModel]="chosen() ?? ready[0].diaryId" (ngModelChange)="chosen.set($event)">
                  @for (grow of ready; track grow.diaryId) {
                    <option [value]="grow.diaryId">{{ grow.title }}</option>
                  }
                </select>
              </div>
            } @else {
              <p class="text-sm text-fg-muted">Cultivo: <a [routerLink]="['/diarios', ready[0].diaryId]" class="underline">{{ ready[0].title }}</a></p>
            }
            @if (error()) {
              <p role="alert" class="text-sm text-danger">{{ error() }}</p>
            }
            <button type="button" class="wd-button w-fit" [disabled]="opening()" (click)="open(chosen() ?? ready[0].diaryId)">
              Avaliar agora
            </button>
          }
        } @else if (growing(list); as current) {
          <p class="text-sm">
            Você está cultivando esta strain em <a [routerLink]="['/diarios', current.diaryId]" class="font-semibold underline">{{ current.title }}</a>.
            Quando concluir o cultivo, a avaliação abre aqui.
          </p>
        } @else if (list.length) {
          <p class="text-sm">Você já avaliou todos os seus cultivos desta strain. Obrigado!</p>
          <a routerLink="/diarios/novo" [queryParams]="{ strainId: strainId() }" class="text-sm text-accent underline">Cultivar de novo</a>
        } @else {
          <p class="text-sm text-fg-muted">
            Aqui só avalia quem cultivou: as notas vêm de cultivos reais, por isso dá pra confiar nelas. Registre um
            diário com esta strain e, quando concluir, é só voltar aqui. Cada avaliação vale pontos no ranking de growers.
          </p>
          <a routerLink="/diarios/novo" [queryParams]="{ strainId: strainId() }" class="wd-button w-fit">
            <app-icon name="diaries" class="size-5" /> Iniciar diário com esta strain
          </a>
        }
      } @else {
        <div class="h-10 animate-pulse rounded-xl bg-surface-raised" aria-busy="true"></div>
      }
    </section>
  `,
})
export class StrainReviewPanel {
  private readonly api = inject(CatalogApi);
  private readonly diaries = inject(DiariesApi);
  protected readonly auth = inject(AuthService);

  readonly strainId = input.required<string>();
  readonly strainName = input.required<string>();
  /** Avaliação publicada: a página recarrega as médias e a lista. */
  readonly reviewed = output<void>();

  protected readonly chosen = signal<string | null>(null);
  protected readonly form = signal<DiaryCard | null>(null);
  protected readonly opening = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  /** null enquanto carrega; visitantes não consultam. */
  protected readonly grows = toSignal(
    combineLatest([toObservable(computed(() => (this.auth.isLoggedIn() ? this.strainId() : null))), this.reload$]).pipe(
      switchMap(([id]) =>
        id ? this.api.myStrainGrows(id).pipe(catchError(() => of<OwnGrow[]>([]))) : of<OwnGrow[] | null>(null),
      ),
    ),
    { initialValue: null },
  );

  protected pending(list: OwnGrow[]): OwnGrow[] | null {
    const ready = list.filter((grow) => grow.completed && !grow.reviewed);
    return ready.length ? ready : null;
  }

  protected growing(list: OwnGrow[]): OwnGrow | undefined {
    return list.find((grow) => !grow.completed);
  }

  protected open(diaryId: string): void {
    this.opening.set(true);
    this.error.set(null);
    this.diaries.get(diaryId).subscribe({
      next: (diary) => {
        this.form.set(diary);
        this.opening.set(false);
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.opening.set(false);
      },
    });
  }

  protected onSubmitted(): void {
    this.form.set(null);
    this.chosen.set(null);
    this.sent.set(true);
    this.reload$.next();
    this.reviewed.emit();
  }
}
