import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { BreederProgram, BreederProgramApi, Representation } from '../core/api/breeder-program.api';
import { CatalogItem, Dominance, FloweringSpeed, SeedKind, StrainTraits } from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { CatalogPicker } from '../shared/catalog-picker';
import { Icon } from '../shared/icon';
import {
  BREEDER_KIND_LABEL,
  BreederKind,
  DOMINANCE_LABEL,
  GENERATION_LABEL,
  SEED_KIND_LABEL,
  SPEED_LABEL,
  StrainGeneration,
  formatDate,
  options,
} from '../shared/labels';
import { TestingProgressBar } from '../shared/testing-progress';

const NOTE_MIN = 10;
const NOTE_MAX = 500;

/**
 * Programa de breeders, para bancos de sementes e para breeders independentes (quem cria genéticas por conta
 * própria). Quem é aprovado pela moderação cadastra as sementes; elas ficam "em teste" e só entram no banco oficial
 * quando 3 cultivadores de fora do breeder as cultivam e há uma avaliação pós-colheita. Quem representa um breeder
 * não aparece publicamente, a não ser que um breeder independente escolha aparecer como criador(a).
 * <p>
 * A apresentação é pública (é o convite para breeders conhecerem o programa); pedir para entrar, cadastrar sementes e
 * acompanhar o teste pedem conta.
 */
@Component({
  selector: 'app-breeder-program-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CatalogPicker, Icon, TestingProgressBar],
  template: `
    <a routerLink="/strains" class="text-sm text-fg-muted">← Catálogo</a>
    <section class="relative mt-2 overflow-hidden rounded-3xl border border-accent/25 bg-linear-to-br from-accent/20 via-surface-raised to-surface p-6">
      <app-icon name="sprout" class="pointer-events-none absolute -right-4 -top-4 size-32 text-accent/10" [strokeWidth]="1.2" />
      <p class="text-sm font-semibold uppercase tracking-wider text-accent">Feito pela comunidade</p>
      <h1 class="mt-1 text-2xl font-bold">Programa de breeders</h1>
      <p class="mt-2 max-w-2xl text-fg-muted">
        Para bancos de sementes e para breeders independentes — quem cria genéticas em casa ou em pequenos projetos —
        colocarem suas genéticas no catálogo, validadas por quem cultiva. Sem anúncio pago e sem atalho: quem decide se a
        semente é boa são os growers.
      </p>
      @if (!auth.isLoggedIn()) {
        <div class="mt-4 flex flex-wrap gap-2">
          <a routerLink="/entrar" [queryParams]="{ voltar: '/breeders/programa' }" class="wd-button">Entrar e participar</a>
          <a routerLink="/cadastro" class="wd-button bg-surface-raised text-fg">Criar conta</a>
        </div>
      }
    </section>

    <h2 class="mt-6 text-lg font-semibold">Como funciona</h2>
    <ol class="wd-card mt-3 grid gap-2 text-sm">
      <li>
        <strong>1. Peça para entrar.</strong> Se for um banco, mostre o vínculo (site oficial, rede do banco). Se for breeder
        independente, cadastre o nome do seu projeto e mostre seu trabalho (diários de polinização aqui no weediário, fotos,
        perfil onde você divulga). A moderação confere; ninguém mais vê seu pedido.
      </li>
      <li><strong>2. Cadastre as sementes.</strong> Elas entram como <em>em teste</em>: aparecem para quem for abrir um diário, mas ainda não no catálogo oficial.</li>
      <li>
        <strong>3. A comunidade valida.</strong> Com 3 cultivadores de fora do breeder e 1 avaliação pós-colheita, a semente entra no
        banco oficial e no <a routerLink="/dados-abertos" class="text-accent underline">banco aberto</a>. Diários e avaliações de
        quem representa o breeder não contam.
      </li>
    </ol>

    <section class="mt-6" aria-labelledby="where-title">
      <h2 id="where-title" class="text-lg font-semibold">Onde suas genéticas aparecem</h2>
      <p class="text-sm text-fg-muted">Depois de validadas, elas entram em tudo que a comunidade consulta. Os números vêm de cultivos reais, não de quem vende. Rankings e receitas ficam para quem tem conta.</p>
      <div class="mt-3 grid gap-3 sm:grid-cols-2">
        <a routerLink="/mais-usados" class="wd-card grid gap-1 hover:border-accent/50">
          <span class="flex items-center gap-2 font-semibold"><app-icon name="trophy" class="size-5 text-accent" /> Ranking de strains</span>
          <span class="text-sm text-fg-muted">As genéticas mais cultivadas, pelo número de diários públicos e de growers, com a média de gramas por planta das colheitas.</span>
        </a>
        <a routerLink="/mais-usados" [queryParams]="{ tipo: 'BREEDERS' }" class="wd-card grid gap-1 hover:border-accent/50">
          <span class="flex items-center gap-2 font-semibold"><app-icon name="sprout" class="size-5 text-accent" /> Ranking de breeders</span>
          <span class="text-sm text-fg-muted">O seu banco ou projeto ao lado dos outros, pelo quanto as suas sementes são cultivadas.</span>
        </a>
        <a routerLink="/strains" class="wd-card grid gap-1 hover:border-accent/50">
          <span class="flex items-center gap-2 font-semibold"><app-icon name="star" class="size-5 text-accent" /> Avaliações de quem colheu</span>
          <span class="text-sm text-fg-muted">
            Notas de vigor, resistência, facilidade, rendimento e aroma na página de cada strain, e de germinação,
            estabilidade e atendimento na página do breeder. Só avalia quem concluiu o cultivo.
          </span>
        </a>
        <a routerLink="/dados-abertos" class="wd-card grid gap-1 hover:border-accent/50">
          <span class="flex items-center gap-2 font-semibold"><app-icon name="database" class="size-5 text-accent" /> Banco aberto</span>
          <span class="text-sm text-fg-muted">As strains oficiais vão para o banco aberto de strains, livre para qualquer pessoa baixar e usar.</span>
        </a>
      </div>
      <p class="mt-3 text-sm text-fg-muted">
        O mesmo ranking vale para insumos: <a routerLink="/mais-usados" [queryParams]="{ tipo: 'BRANDS' }" class="text-accent underline">marcas</a> e
        <a routerLink="/mais-usados" [queryParams]="{ tipo: 'PRODUCTS' }" class="text-accent underline">produtos</a>
        (nutrientes, substratos, aditivos) entram pelo uso nos diários da comunidade. Sementes ainda em teste não entram nos
        rankings nem nas médias.
      </p>
    </section>

    @if (!auth.isLoggedIn()) {
      <section class="wd-card mt-6 grid gap-2 border-accent/40" aria-labelledby="join-title">
        <h2 id="join-title" class="text-lg font-semibold">Quer colocar suas genéticas no catálogo?</h2>
        <p class="text-sm text-fg-muted">
          Crie uma conta (só apelido e senha) e peça para entrar no programa. A moderação confere o vínculo com o banco ou o
          seu trabalho como breeder, e ninguém mais vê o pedido.
        </p>
        <div class="flex flex-wrap gap-2">
          <a routerLink="/entrar" [queryParams]="{ voltar: '/breeders/programa' }" class="wd-button">Entrar e participar</a>
          <a routerLink="/cadastro" class="wd-button bg-surface-raised text-fg">Criar conta</a>
        </div>
      </section>
    } @else if (program(); as state) {
      @if (state.error) {
        <p role="alert" class="wd-card mt-4 text-danger">{{ state.error }}</p>
      }

      <section class="mt-6" aria-labelledby="representations-title">
        <h2 id="representations-title" class="text-lg font-semibold">Breeders que você representa</h2>
        <ul class="mt-3 grid gap-2">
          @for (rep of state.data.representations; track rep.breederId) {
            <li class="wd-card flex flex-wrap items-center gap-3">
              <span class="min-w-0 flex-1">
                <a [routerLink]="['/bancos', rep.breederId]" class="font-semibold">{{ rep.breeder }}</a>
                <span class="block text-xs text-fg-muted">{{ kindLabel[rep.claimedKind] }}</span>
                <span class="block text-xs text-fg-muted">
                  @if (rep.status === 'APPROVED') {
                    Aprovado em {{ date(rep.decidedAt) }}
                  } @else {
                    Pedido em {{ date(rep.requestedAt) }} · aguardando a moderação
                  }
                </span>
              </span>
              <span class="rounded-full px-2 py-0.5 text-xs font-semibold" [class]="rep.status === 'APPROVED' ? 'bg-accent/15 text-accent' : 'bg-warn/15 text-warn'">
                {{ rep.status === 'APPROVED' ? 'aprovado' : 'pendente' }}
              </span>
              <button type="button" class="wd-button min-h-9 bg-surface-raised text-fg" [disabled]="busy()" (click)="withdraw(rep)">
                {{ rep.status === 'APPROVED' ? 'Deixar de representar' : 'Cancelar pedido' }}
              </button>
              @if (rep.claimedKind === 'INDEPENDENT') {
                <label class="flex w-full items-start gap-2 text-sm">
                  <input type="checkbox" class="mt-0.5 size-5" [checked]="rep.publicCredit" [disabled]="busy()"
                         (change)="changeCredit(rep, $any($event.target).checked)" />
                  <span>
                    Mostrar meu apelido como criador(a) das genéticas na página do breeder.
                    <span class="block text-xs text-fg-muted">Desligado, ninguém sabe que é você. Dá para mudar quando quiser.</span>
                  </span>
                </label>
              }
            </li>
          } @empty {
            <li class="wd-card text-sm text-fg-muted">Você ainda não representa nenhum banco ou breeder.</li>
          }
        </ul>

        <form class="wd-card mt-3 grid gap-3" (ngSubmit)="request()" aria-labelledby="request-title">
          <h3 id="request-title" class="font-semibold">Pedir para entrar no programa</h3>
          <fieldset>
            <legend class="wd-label">Você é</legend>
            <div class="flex flex-wrap gap-4">
              @for (o of kindOptions; track o.value) {
                <label class="inline-flex min-h-11 items-center gap-2">
                  <input type="radio" class="size-5" name="kind" [value]="o.value" [(ngModel)]="requestKind" />
                  {{ o.value === 'SEEDBANK' ? 'De um banco de sementes' : 'Breeder independente' }}
                </label>
              }
            </div>
          </fieldset>
          <app-catalog-picker
            kind="breeders"
            [label]="requestKind === 'INDEPENDENT' ? 'Nome do seu projeto de breeding' : 'Banco de sementes'"
            [placeholder]="requestKind === 'INDEPENDENT' ? 'Ex.: Quintal Genetics' : 'Busque o banco no catálogo'"
            [hint]="requestKind === 'INDEPENDENT' ? 'Se ainda não estiver no catálogo, cadastre o nome aqui mesmo.' : ''"
            [(value)]="breeder"
          />
          <div>
            <label class="wd-label" for="rep-note">Como comprovar (só a moderação vê)</label>
            <textarea id="rep-note" rows="3" class="wd-input py-2 text-base" [attr.minlength]="noteMin" [attr.maxlength]="noteMax"
                      [placeholder]="requestKind === 'INDEPENDENT'
                        ? 'Ex.: faço cruzamentos há 3 anos; os diários de polinização das minhas F1 estão no meu perfil aqui.'
                        : 'Ex.: sou o fundador; o site oficial do banco cita meu apelido na página Sobre.'"
                      [(ngModel)]="note" name="note"></textarea>
            <p class="mt-1 text-xs text-fg-muted">Sem telefone, PIX ou contato para negócio. A moderação pode pedir mais informações.</p>
          </div>
          @if (requestKind === 'INDEPENDENT') {
            <label class="flex items-start gap-2 text-sm">
              <input type="checkbox" class="mt-0.5 size-5" [(ngModel)]="publicCredit" name="publicCredit" />
              <span>
                Quero aparecer como criador(a) das genéticas (meu apelido na página do breeder).
                <span class="block text-xs text-fg-muted">Opcional. Sem isso, ninguém sabe quem está por trás do projeto.</span>
              </span>
            </label>
          }
          <button type="submit" class="wd-button w-fit" [disabled]="busy() || !breeder() || note.trim().length < noteMin">Enviar pedido</button>
        </form>
      </section>

      @if (approved().length) {
        <section class="mt-6" aria-labelledby="register-title">
          <h2 id="register-title" class="text-lg font-semibold">Cadastrar semente para teste</h2>
          <form class="wd-card mt-3 grid gap-3 sm:grid-cols-2" (ngSubmit)="register()">
            <div>
              <label class="wd-label" for="s-breeder">Breeder</label>
              <select id="s-breeder" class="wd-input" [(ngModel)]="strainBreederId" name="breederId">
                <option [ngValue]="undefined" disabled>Escolha o breeder</option>
                @for (rep of approved(); track rep.breederId) {
                  <option [ngValue]="rep.breederId">{{ rep.breeder }}</option>
                }
              </select>
            </div>
            <div>
              <label class="wd-label" for="s-name">Nome da strain</label>
              <input id="s-name" class="wd-input" maxlength="80" required [(ngModel)]="strainName" name="name" />
            </div>
            <div>
              <label class="wd-label" for="s-flowering">Floração</label>
              <select id="s-flowering" class="wd-input" [(ngModel)]="traits.floweringType" name="floweringType">
                <option [ngValue]="undefined">Não informar</option>
                <option ngValue="PHOTOPERIOD">Fotoperíodo</option>
                <option ngValue="AUTOFLOWER">Automática</option>
              </select>
            </div>
            <div>
              <label class="wd-label" for="s-dominance">Dominância</label>
              <select id="s-dominance" class="wd-input" [(ngModel)]="traits.dominance" name="dominance">
                <option [ngValue]="undefined">Não informar</option>
                @for (o of dominanceOptions; track o.value) {
                  <option [ngValue]="o.value">{{ o.label }}</option>
                }
              </select>
            </div>
            <div>
              <label class="wd-label" for="s-speed">Tempo de flora</label>
              <select id="s-speed" class="wd-input" [(ngModel)]="traits.floweringSpeed" name="floweringSpeed">
                <option [ngValue]="undefined">Não informar</option>
                @for (o of speedOptions; track o.value) {
                  <option [ngValue]="o.value">{{ o.label }}</option>
                }
              </select>
            </div>
            <fieldset>
              <legend class="wd-label">Sementes</legend>
              <div class="flex flex-wrap gap-4">
                @for (o of seedOptions; track o.value) {
                  <label class="inline-flex min-h-11 items-center gap-2">
                    <input type="checkbox" class="size-5" [checked]="seedTypes.has(o.value)" (change)="toggleSeed(o.value)" />
                    {{ o.label }}
                  </label>
                }
              </div>
            </fieldset>
            <div>
              <label class="wd-label" for="s-genetics">Genética (cruzamento)</label>
              <input id="s-genetics" class="wd-input" maxlength="120" placeholder="Ex.: Gelato 41 x Zkittlez" [(ngModel)]="traits.genetics" name="genetics" />
            </div>
            <div>
              <label class="wd-label" for="s-generation">Geração</label>
              <select id="s-generation" class="wd-input" [(ngModel)]="traits.generation" name="generation">
                <option [ngValue]="undefined">Não informar</option>
                @for (o of generationOptions; track o.value) {
                  <option [ngValue]="o.value">{{ o.label }}</option>
                }
              </select>
              <p class="mt-1 text-xs text-fg-muted">Ajuda quem vai testar a saber o quanto as plantas podem variar.</p>
            </div>
            <div>
              <label class="wd-label" for="s-flavors">Sabores (separe por vírgula)</label>
              <input id="s-flavors" class="wd-input" placeholder="Ex.: cítrico, doce" [(ngModel)]="flavorsText" name="flavors" />
            </div>
            <div>
              <label class="wd-label" for="s-effects">Efeitos (separe por vírgula)</label>
              <input id="s-effects" class="wd-input" placeholder="Ex.: relaxante" [(ngModel)]="effectsText" name="effects" />
            </div>
            <div class="sm:col-span-2">
              <button type="submit" class="wd-button" [disabled]="busy() || !strainName.trim()">Cadastrar para teste</button>
            </div>
          </form>
        </section>
      }

      @if (state.data.strains.length) {
        <section class="mt-6" aria-labelledby="mine-title">
          <h2 id="mine-title" class="text-lg font-semibold">Suas sementes</h2>
          <ul class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (strain of state.data.strains; track strain.id) {
              <li class="wd-card grid gap-2">
                <p class="flex items-start justify-between gap-2">
                  <a [routerLink]="['/strains', strain.id]" class="min-w-0 truncate font-semibold">{{ strain.name }}</a>
                  @if (strain.progress) {
                    <span class="shrink-0 rounded-full bg-warn/15 px-2 py-0.5 text-xs font-semibold text-warn">em teste</span>
                  } @else {
                    <span class="inline-flex shrink-0 items-center gap-1 rounded-full bg-accent/15 px-2 py-0.5 text-xs font-semibold text-accent">
                      <app-icon name="check" class="size-3" /> oficial
                    </span>
                  }
                </p>
                <p class="text-xs text-fg-muted">
                  {{ strain.breeder }}{{ strain.generation ? ' · ' + generationLabel[strain.generation] : '' }} · cadastrada em {{ date(strain.createdAt) }}
                </p>
                @if (strain.progress; as progress) {
                  <app-testing-progress [progress]="progress" />
                } @else if (strain.promotedAt) {
                  <p class="text-sm">No banco oficial desde {{ date(strain.promotedAt) }}.</p>
                }
              </li>
            }
          </ul>
        </section>
      }
    } @else {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }

    @if (message()) {
      <p role="status" class="mt-4 text-sm">{{ message() }}</p>
    }
  `,
})
export class BreederProgramPage {
  private readonly api = inject(BreederProgramApi);
  protected readonly auth = inject(AuthService);

  protected readonly noteMin = NOTE_MIN;
  protected readonly noteMax = NOTE_MAX;
  protected readonly dominanceOptions = options(DOMINANCE_LABEL);
  protected readonly speedOptions = options(SPEED_LABEL);
  protected readonly seedOptions = options(SEED_KIND_LABEL);
  protected readonly kindOptions = options(BREEDER_KIND_LABEL);
  protected readonly kindLabel = BREEDER_KIND_LABEL;
  protected readonly generationOptions = options(GENERATION_LABEL);
  protected readonly generationLabel = GENERATION_LABEL;

  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly breeder = signal<CatalogItem | null>(null);
  protected note = '';
  protected requestKind: BreederKind = 'SEEDBANK';
  protected publicCredit = false;
  protected strainBreederId: string | undefined;
  protected strainName = '';
  protected traits: {
    floweringType?: StrainTraits['floweringType'];
    dominance?: Dominance;
    floweringSpeed?: FloweringSpeed;
    genetics?: string;
    generation?: StrainGeneration;
  } = {};
  protected readonly seedTypes = new Set<SeedKind>();
  protected flavorsText = '';
  protected effectsText = '';

  private readonly reload = signal(0);
  /** Só quem está logado consulta o próprio programa; visitantes veem apenas a apresentação. */
  protected readonly program = toSignal(
    toObservable(computed(() => ({ loggedIn: this.auth.isLoggedIn(), reload: this.reload() }))).pipe(
      switchMap(({ loggedIn }) =>
        !loggedIn ? of(null) : this.api.mine().pipe(
          map((data) => ({ data, error: null as string | null })),
          catchError((error) => of({ data: { representations: [], strains: [] } as BreederProgram, error: problemMessage(error) })),
        ),
      ),
    ),
  );
  protected readonly approved = computed(() => (this.program()?.data.representations ?? []).filter((rep) => rep.status === 'APPROVED'));

  protected date(iso: string | undefined): string {
    return formatDate(iso);
  }

  protected toggleSeed(kind: SeedKind): void {
    if (this.seedTypes.has(kind)) {
      this.seedTypes.delete(kind);
    } else {
      this.seedTypes.add(kind);
    }
  }

  protected request(): void {
    const breeder = this.breeder();
    if (!breeder) {
      return;
    }
    const kind = this.requestKind;
    this.run(this.api.requestRepresentation(breeder.id, this.note.trim(), kind, kind === 'INDEPENDENT' && this.publicCredit), () => {
      this.message.set(`Pedido enviado. A moderação vai conferir seu vínculo com ${breeder.name}.`);
      this.breeder.set(null);
      this.note = '';
      this.publicCredit = false;
    });
  }

  protected changeCredit(rep: Representation, publicCredit: boolean): void {
    this.run(this.api.changeCredit(rep.breederId, publicCredit), () =>
      this.message.set(publicCredit ? `Seu apelido aparece como criador(a) em ${rep.breeder}.` : `Você não aparece mais em ${rep.breeder}.`),
    );
  }

  protected withdraw(rep: Representation): void {
    const question = rep.status === 'APPROVED' ? `Deixar de representar ${rep.breeder}?` : `Cancelar o pedido para ${rep.breeder}?`;
    if (!confirm(question)) {
      return;
    }
    this.run(this.api.withdraw(rep.breederId), () => this.message.set(null));
  }

  protected register(): void {
    const breederId = this.strainBreederId ?? (this.approved().length === 1 ? this.approved()[0].breederId : undefined);
    if (!breederId) {
      this.message.set('Escolha o breeder da semente.');
      return;
    }
    const traits: StrainTraits = {
      ...this.traits,
      seedTypes: this.seedTypes.size ? [...this.seedTypes] : undefined,
      flavors: splitTags(this.flavorsText),
      effects: splitTags(this.effectsText),
    };
    const name = this.strainName.trim();
    this.run(this.api.registerStrain(breederId, name, traits), () => {
      this.message.set(`${name} entrou em teste. Agora é com a comunidade!`);
      this.strainName = '';
      this.traits = {};
      this.seedTypes.clear();
      this.flavorsText = '';
      this.effectsText = '';
    });
  }

  private run(request: Observable<unknown>, done: () => void): void {
    this.busy.set(true);
    this.message.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        done();
        this.reload.update((n) => n + 1);
      },
      error: (error) => {
        this.busy.set(false);
        this.message.set(problemMessage(error));
      },
    });
  }
}

function splitTags(text: string): string[] | undefined {
  const tags = text.split(',').map((tag) => tag.trim()).filter(Boolean);
  return tags.length ? tags : undefined;
}
