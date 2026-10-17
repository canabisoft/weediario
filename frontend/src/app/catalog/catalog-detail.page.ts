import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, forkJoin, map, of, switchMap } from 'rxjs';
import { CatalogApi } from '../core/api/catalog.api';
import {
  CatalogDbApi,
  CatalogItem,
  CatalogSource,
  Dominance,
  FloweringSpeed,
  SeedKind,
  StrainTraits,
  TestingProgress,
} from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { CatalogPicker } from '../shared/catalog-picker';
import { formatCompact, formatDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { RatingBar } from '../shared/rating';
import { SeedbankShowcase } from './seedbank-showcase';
import { StrainReviewPanel } from './strain-review-panel';
import { TestingStrains } from './testing-strains';
import { BREEDER_KIND_LABEL, DOMINANCE_LABEL, formatDate, GENERATION_LABEL, options, SEED_KIND_LABEL, SOURCE_LABEL, SPEED_LABEL } from '../shared/labels';
import { TestingProgressBar } from '../shared/testing-progress';

export type CatalogKind = 'strain' | 'seedbank';

interface Review {
  readonly id: string;
  readonly author: string;
  readonly diaryId?: string;
  readonly context?: string;
  readonly scores: { label: string; value: number }[];
  readonly report: string;
}

interface Detail {
  readonly id: string;
  readonly name: string;
  readonly subtitle?: string;
  readonly breederId?: string;
  /** Banco de sementes ou breeder independente. */
  readonly kindLabel?: string;
  /** Breeders independentes que escolheram aparecer como criadores. */
  readonly creators?: string[];
  readonly verified: boolean;
  /** Semente de breeder ainda em teste pela comunidade (com o andamento). */
  readonly testing?: TestingProgress;
  /** Data em que a semente saiu do teste e entrou no banco oficial. */
  readonly promotedAt?: string;
  readonly source: CatalogSource;
  readonly facts: { label: string; value: string }[];
  readonly tags: { label: string; values: string[] }[];
  readonly traits?: StrainTraits;
  readonly diaries: number;
  readonly avgGramsPerPlant?: number;
  readonly reviewCount: number;
  readonly overall?: number;
  readonly criteria: { label: string; value: number }[];
  readonly reviews: Review[];
}

const EMPTY_PAGE = { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 };

/**
 * Detalhe de uma strain ou de um breeder (banco de sementes ou independente): características do catálogo (base pública + comunidade),
 * uso nos diários, médias das reviews e curadoria (completar informações; moderadores verificam e juntam duplicatas).
 */
import { ReportButton } from '../shared/report-button';

@Component({
  selector: 'app-catalog-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, RatingBar, CatalogPicker, SeedbankShowcase, TestingStrains, TestingProgressBar, ReportButton, StrainReviewPanel],
  template: `
    <a routerLink="/strains" [queryParams]="kind() === 'seedbank' ? { aba: 'bancos' } : {}" class="text-sm text-fg-muted">
      ← {{ kind() === 'strain' ? 'Strains' : 'Bancos e breeders' }}
    </a>
    @if (detail(); as state) {
      @if (state.detail; as d) {
        <header class="mt-2 flex items-start justify-between gap-4">
          <div>
            <h1 class="flex items-center gap-2 text-2xl font-bold">
              {{ d.name }}
              @if (d.verified) {
                <span class="inline-flex items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-xs font-semibold text-accent">
                  <app-icon name="check" class="size-3" /> verificado
                </span>
              }
              @if (d.testing) {
                <span class="inline-flex items-center rounded-full bg-warn/15 px-2 py-0.5 text-xs font-semibold text-warn">em teste</span>
              }
              @if (d.kindLabel) {
                <span class="inline-flex items-center rounded-full bg-surface-raised px-2 py-0.5 text-xs font-semibold text-fg-muted">{{ d.kindLabel }}</span>
              }
            </h1>
            @if (d.creators?.length) {
              <p class="text-sm text-fg-muted">
                Genéticas criadas por
                @for (creator of d.creators; track creator; let last = $last) {
                  <a [routerLink]="['/growers', creator]" class="font-semibold text-fg underline">{{ creator }}</a>{{ last ? '' : ', ' }}
                }
              </p>
            }
            @if (d.subtitle) {
              @if (d.breederId) {
                <a [routerLink]="['/bancos', d.breederId]" class="text-fg-muted underline">{{ d.subtitle }}</a>
              } @else {
                <p class="text-fg-muted">{{ d.subtitle }}</p>
              }
            }
            <p class="mt-1 text-xs text-fg-muted">Fonte: {{ source[d.source] }} · {{ d.diaries }} {{ d.diaries === 1 ? 'diário público' : 'diários públicos' }}</p>
            @if (d.promotedAt) {
              <p class="mt-1 text-xs text-fg-muted">Entrou no banco oficial em {{ date(d.promotedAt) }}, depois do teste da comunidade.</p>
            }
          </div>
          <div class="flex shrink-0 flex-col items-end gap-2">
            @if (d.overall != null) {
              <p class="inline-flex items-center gap-1 text-3xl font-bold tabular-nums">
                <app-icon name="star" class="size-6 text-accent" [filled]="true" />{{ num(d.overall) }}
              </p>
            }
            @if (kind() === 'strain') {
              <button type="button" class="wd-button" (click)="scrollToRating()">
                <app-icon name="star" class="size-5" /> Avaliar
              </button>
            }
          </div>
        </header>

        @if (d.testing; as progress) {
          <section class="wd-card mt-4 grid gap-3 border-warn/40" aria-labelledby="testing-title">
            <div>
              <h2 id="testing-title" class="font-semibold">Semente em teste</h2>
              <p class="text-sm text-fg-muted">
                Cadastrada pelo próprio breeder. Ela entra no banco oficial quando o breeder colhe e publica a primeira
                avaliação do que esperar, {{ progress.testersRequired }} cultivadores de fora do breeder abrem um diário
                com ela e há {{ progress.reviewsRequired }} avaliação pós-colheita de fora. Até lá fica fora da listagem
                oficial, dos rankings e do banco aberto.
              </p>
            </div>
            <app-testing-progress [progress]="progress" />
            <p class="text-sm">
              Quer ajudar a testar? <a routerLink="/diarios/novo" class="text-accent underline">Inicie um diário</a> e escolha esta strain.
            </p>
          </section>
        }

        @if (d.facts.length || d.tags.length) {
          <section class="wd-card mt-4 grid gap-3">
            @if (d.facts.length) {
              <dl class="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
                @for (fact of d.facts; track fact.label) {
                  <div><dt class="text-xs text-fg-muted">{{ fact.label }}</dt><dd class="font-semibold">{{ fact.value }}</dd></div>
                }
              </dl>
            }
            @for (group of d.tags; track group.label) {
              <div>
                <p class="text-xs text-fg-muted">{{ group.label }}</p>
                <ul class="mt-1 flex flex-wrap gap-1.5">
                  @for (tag of group.values; track tag) {
                    <li class="rounded-full bg-surface-raised px-2.5 py-0.5 text-sm">{{ tag }}</li>
                  }
                </ul>
              </div>
            }
          </section>
        }

        @if (kind() === 'seedbank') {
          <app-seedbank-showcase [breederId]="d.id" />
          <app-testing-strains [breederId]="d.id" />
        }

        @if (kind() === 'strain' && auth.isLoggedIn()) {
          <details class="wd-card mt-4">
            <summary class="cursor-pointer font-semibold">{{ moderator() ? 'Editar informações (moderação)' : 'Completar informações' }}</summary>
            <p class="mt-1 text-xs text-fg-muted">
              {{ moderator() ? 'Como moderador, você pode corrigir qualquer campo.' : 'Só dá para preencher o que ainda está vazio: nada do que já foi informado é sobrescrito.' }}
            </p>
            <form class="mt-3 grid gap-3 sm:grid-cols-2" (ngSubmit)="contribute(d)">
              @if (moderator() || !d.traits?.floweringType) {
                <div>
                  <label class="wd-label" for="c-flowering">Floração</label>
                  <select id="c-flowering" class="wd-input" [(ngModel)]="draft.floweringType" name="floweringType">
                    <option [ngValue]="undefined">Não sei</option>
                    <option ngValue="PHOTOPERIOD">Fotoperíodo</option>
                    <option ngValue="AUTOFLOWER">Automática</option>
                  </select>
                </div>
              }
              @if (moderator() || !d.traits?.dominance) {
                <div>
                  <label class="wd-label" for="c-dominance">Dominância</label>
                  <select id="c-dominance" class="wd-input" [(ngModel)]="draft.dominance" name="dominance">
                    <option [ngValue]="undefined">Não sei</option>
                    @for (o of dominanceOptions; track o.value) {
                      <option [ngValue]="o.value">{{ o.label }}</option>
                    }
                  </select>
                </div>
              }
              @if (moderator() || !d.traits?.floweringSpeed) {
                <div>
                  <label class="wd-label" for="c-speed">Tempo de flora</label>
                  <select id="c-speed" class="wd-input" [(ngModel)]="draft.floweringSpeed" name="floweringSpeed">
                    <option [ngValue]="undefined">Não sei</option>
                    @for (o of speedOptions; track o.value) {
                      <option [ngValue]="o.value">{{ o.label }}</option>
                    }
                  </select>
                </div>
              }
              @if (moderator() || !d.traits?.genetics) {
                <div>
                  <label class="wd-label" for="c-genetics">Genética (cruzamento)</label>
                  <input id="c-genetics" class="wd-input" maxlength="120" placeholder="Ex.: Bubble Gum x OG Kush" [(ngModel)]="draft.genetics" name="genetics" />
                </div>
              }
              @if (moderator() || !d.traits?.flavors?.length) {
                <div>
                  <label class="wd-label" for="c-flavors">Sabores (separe por vírgula)</label>
                  <input id="c-flavors" class="wd-input" placeholder="Ex.: terroso, café" [(ngModel)]="flavorsText" name="flavors" />
                </div>
              }
              @if (moderator() || !d.traits?.effects?.length) {
                <div>
                  <label class="wd-label" for="c-effects">Efeitos (separe por vírgula)</label>
                  <input id="c-effects" class="wd-input" placeholder="Ex.: relaxante, sonolento" [(ngModel)]="effectsText" name="effects" />
                </div>
              }
              <div class="flex items-center gap-3 sm:col-span-2">
                <button type="submit" class="wd-button" [disabled]="busy()">Enviar</button>
                @if (message()) {
                  <span role="status" class="text-sm text-fg-muted">{{ message() }}</span>
                }
              </div>
            </form>
          </details>
        }

        @if (moderator()) {
          <section class="wd-card mt-4 grid gap-3 border-warn/40" aria-label="Moderação">
            <p class="font-semibold">Moderação</p>
            @if (d.testing) {
              <p class="text-sm text-fg-muted">
                Sementes em teste não podem ser verificadas à mão: entram no banco oficial sozinhas quando o teste da
                comunidade termina. Se for spam ou cadastro errado e ninguém estiver usando, dá para recusar.
              </p>
              <button type="button" class="wd-button w-fit bg-surface-raised text-fg" (click)="reject(d)">
                <app-icon name="trash" class="size-5" /> Recusar cadastro
              </button>
            } @else if (!d.verified) {
              <button type="button" class="wd-button w-fit" (click)="verify(d)"><app-icon name="check" class="size-5" /> Marcar como verificado</button>
            }
            @if (kind() === 'strain') {
              <app-catalog-picker kind="strains" label="Esta strain é duplicata de…" placeholder="Busque a strain principal" [(value)]="mergeTarget" />
              <button type="button" class="wd-button w-fit bg-surface-raised text-fg" [disabled]="!mergeTarget()" (click)="merge(d)">
                Juntar nesta principal
              </button>
              <p class="text-xs text-fg-muted">Diários e reviews passam para a principal, e este nome vira apelido dela.</p>
            }
            @if (modMessage()) {
              <p role="status" class="text-sm">{{ modMessage() }}</p>
            }
          </section>
        }

        @if (kind() === 'strain') {
          <app-strain-review-panel [strainId]="d.id" [strainName]="d.name" (reviewed)="reload$.next()" />
        }

        @if (d.criteria.length) {
          <section class="wd-card mt-4 grid gap-2" aria-label="Médias por critério">
            <p class="text-sm text-fg-muted">{{ d.reviewCount }} {{ d.reviewCount === 1 ? 'avaliação' : 'avaliações' }}</p>
            @for (criterion of d.criteria; track criterion.label) {
              <app-rating-bar [label]="criterion.label" [value]="criterion.value" />
            }
          </section>
        }

        <h2 class="mt-6 text-lg font-semibold">Avaliações</h2>
        <ul class="mt-3 grid gap-3">
          @for (review of d.reviews; track review.id) {
            <li class="wd-card">
              <p class="flex flex-wrap items-center gap-2 text-sm">
                <a [routerLink]="['/growers', review.author]" class="font-semibold">{{ review.author }}</a>
                @if (review.context) {
                  <span class="text-fg-muted">· {{ review.context }}</span>
                }
                @if (review.diaryId) {
                  <a [routerLink]="['/diarios', review.diaryId]" class="ml-auto text-xs text-accent">Ver o diário</a>
                }
                @if (auth.isLoggedIn() && review.author !== auth.me()?.username) {
                  <app-report-button targetType="REVIEW" [targetId]="review.id" />
                }
              </p>
              <p class="mt-1 flex flex-wrap gap-x-3 text-xs text-fg-muted">
                @for (score of review.scores; track score.label) {
                  <span>{{ score.label }} <strong class="text-fg">{{ score.value }}</strong></span>
                }
              </p>
              <p class="mt-2 whitespace-pre-line break-words">{{ review.report }}</p>
            </li>
          } @empty {
            <li class="wd-card text-sm text-fg-muted">
              Ainda sem avaliações.{{ kind() === 'strain' ? ' Cultivou esta strain? Seja a primeira pessoa a avaliar.' : ' Elas chegam quando alguém conclui um cultivo e publica a review.' }}
            </li>
          }
        </ul>
      } @else {
        <p class="wd-card mt-4">{{ state.error }}</p>
      }
    } @else {
      <div class="mt-4 h-60 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class CatalogDetailPage {
  private readonly reviewsApi = inject(CatalogApi);
  private readonly catalog = inject(CatalogDbApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Parâmetro de rota e tipo (data da rota). */
  readonly id = input.required<string>();
  readonly kind = input<CatalogKind>('strain');

  protected readonly source = SOURCE_LABEL;
  protected readonly dominanceOptions = options(DOMINANCE_LABEL);
  protected readonly speedOptions = options(SPEED_LABEL);
  protected readonly moderator = this.auth.isModerator;
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly modMessage = signal<string | null>(null);
  protected readonly mergeTarget = signal<CatalogItem | null>(null);
  protected draft: StrainTraits = {};
  protected flavorsText = '';
  protected effectsText = '';
  protected readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly detail = toSignal(
    combineLatest([toObservable(computed(() => ({ id: this.id(), kind: this.kind() }))), this.reload$]).pipe(
      switchMap(([{ id, kind }]) =>
        (kind === 'strain' ? this.strain(id) : this.seedbank(id)).pipe(
          map((detail) => ({ detail, error: null })),
          catchError((error) => of({ detail: null, error: problemMessage(error, 'Não encontrado.') })),
        ),
      ),
    ),
  );

  protected num(value: number): string {
    return formatDecimal(value, 1);
  }

  protected contribute(detail: Detail): void {
    const traits: StrainTraits = {
      ...this.draft,
      flavors: splitTags(this.flavorsText),
      effects: splitTags(this.effectsText),
    };
    this.busy.set(true);
    this.catalog.contribute(detail.id, traits).subscribe({
      next: (result) => {
        this.message.set(result.changed ? 'Obrigado! Informações adicionadas ao catálogo.' : 'Nada novo para acrescentar.');
        this.busy.set(false);
        this.draft = {};
        this.flavorsText = '';
        this.effectsText = '';
        this.reload$.next();
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected verify(detail: Detail): void {
    this.catalog.verify(this.kind() === 'strain' ? 'strains' : 'breeders', detail.id).subscribe({
      next: () => this.reload$.next(),
      error: (error) => this.modMessage.set(problemMessage(error)),
    });
  }

  protected reject(detail: Detail): void {
    if (!confirm(`Recusar "${detail.name}"? O cadastro será apagado.`)) {
      return;
    }
    this.catalog.reject('strains', detail.id).subscribe({
      next: () => this.router.navigate(['/strains'], { queryParams: { aba: 'teste' } }),
      error: (error) => this.modMessage.set(problemMessage(error)),
    });
  }

  protected scrollToRating(): void {
    document.getElementById('avaliar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected date(iso: string): string {
    return formatDate(iso);
  }

  protected merge(detail: Detail): void {
    const target = this.mergeTarget();
    if (!target || !confirm(`Juntar "${detail.name}" em "${target.name}"? Esta página deixará de existir.`)) {
      return;
    }
    this.catalog.merge('strains', detail.id, target.id).subscribe({
      next: (merged) => this.router.navigate(['/strains', merged.id]),
      error: (error) => this.modMessage.set(problemMessage(error)),
    });
  }

  private strain(id: string): Observable<Detail> {
    return forkJoin([
      this.catalog.strain(id),
      this.reviewsApi.strainReviews(id).pipe(catchError(() => of(EMPTY_PAGE))),
      this.reviewsApi.strain(id).pipe(catchError(() => of(null))),
    ]).pipe(
      map(([s, reviews, summary]): Detail => ({
        id: s.id,
        name: s.name,
        subtitle: s.breeder ?? 'Breeder não informado',
        breederId: s.breederId,
        kindLabel: s.breederKind === 'INDEPENDENT' ? 'de breeder independente' : undefined,
        verified: s.status === 'VERIFIED',
        testing: s.status === 'TESTING' ? s.testing : undefined,
        promotedAt: s.promotedAt,
        source: s.source,
        traits: s,
        facts: [
          s.floweringType ? { label: 'Floração', value: s.floweringType === 'AUTOFLOWER' ? 'Automática' : 'Fotoperíodo' } : null,
          s.dominance ? { label: 'Dominância', value: DOMINANCE_LABEL[s.dominance as Dominance] } : null,
          s.floweringSpeed ? { label: 'Tempo de flora', value: SPEED_LABEL[s.floweringSpeed as FloweringSpeed] } : null,
          s.seedTypes?.length ? { label: 'Sementes', value: s.seedTypes.map((k) => SEED_KIND_LABEL[k as SeedKind]).join(', ') } : null,
          s.genetics ? { label: 'Genética', value: s.genetics } : null,
          s.generation ? { label: 'Geração', value: GENERATION_LABEL[s.generation] } : null,
          s.avgGramsPerPlant != null ? { label: 'Média da comunidade', value: `${formatCompact(s.avgGramsPerPlant)} g/planta` } : null,
        ].filter((fact): fact is { label: string; value: string } => fact !== null),
        tags: [
          s.flavors?.length ? { label: 'Sabores e aromas', values: s.flavors } : null,
          s.effects?.length ? { label: 'Efeitos', values: s.effects } : null,
        ].filter((group): group is { label: string; values: string[] } => group !== null),
        diaries: s.diaries,
        avgGramsPerPlant: s.avgGramsPerPlant,
        reviewCount: s.reviewCount,
        overall: s.reviewCount ? s.overall : undefined,
        criteria: summary?.reviewCount
          ? [
              { label: 'Vigor vegetativo', value: summary.vigor },
              { label: 'Resistência', value: summary.pestResistance },
              { label: 'Facilidade', value: summary.easeOfGrowth },
              { label: 'Rendimento', value: summary.yield },
              { label: 'Aroma e sabor', value: summary.terpeneProfile },
            ]
          : [],
        reviews: reviews.items.map((r) => ({
          ...r,
          scores: [
            { label: 'Vigor', value: r.vigor },
            { label: 'Resistência', value: r.pestResistance },
            { label: 'Facilidade', value: r.easeOfGrowth },
            { label: 'Rendimento', value: r.yield },
            { label: 'Aroma', value: r.terpeneProfile },
          ],
        })),
      })),
    );
  }

  private seedbank(id: string): Observable<Detail> {
    return forkJoin([
      this.catalog.breeder(id),
      this.reviewsApi.seedbankReviews(id).pipe(catchError(() => of(EMPTY_PAGE))),
      this.reviewsApi.seedbank(id).pipe(catchError(() => of(null))),
    ]).pipe(
      map(([b, reviews, summary]): Detail => ({
        id: b.id,
        name: b.name,
        subtitle: `${b.strains} ${b.strains === 1 ? 'strain' : 'strains'} no catálogo`,
        kindLabel: b.kind ? BREEDER_KIND_LABEL[b.kind] : undefined,
        creators: b.creators,
        verified: b.status === 'VERIFIED',
        source: b.source,
        facts: [],
        tags: [],
        diaries: b.diaries,
        reviewCount: b.reviewCount,
        overall: b.reviewCount ? b.overall : undefined,
        criteria: summary?.reviewCount
          ? [
              { label: 'Germinação', value: summary.germination },
              { label: 'Estabilidade genética', value: summary.stability },
              { label: 'Atendimento e entrega', value: summary.service },
            ]
          : [],
        reviews: reviews.items.map((r) => ({
          ...r,
          context: r.strain,
          scores: [
            { label: 'Germinação', value: r.germination },
            { label: 'Estabilidade', value: r.stability },
            { label: 'Atendimento', value: r.service },
          ],
        })),
      })),
    );
  }
}

function splitTags(text: string): string[] | undefined {
  const tags = text.split(',').map((tag) => tag.trim()).filter(Boolean);
  return tags.length ? tags : undefined;
}
