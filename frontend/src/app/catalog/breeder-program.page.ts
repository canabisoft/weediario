import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { BreederProgram, BreederProgramApi, Representation } from '../core/api/breeder-program.api';
import { CatalogItem, Dominance, FloweringSpeed, SeedKind, StrainTraits } from '../core/api/catalog-db.api';
import { problemMessage } from '../core/api/problem';
import { CatalogPicker } from '../shared/catalog-picker';
import { Icon } from '../shared/icon';
import { DOMINANCE_LABEL, SEED_KIND_LABEL, SPEED_LABEL, formatDate, options } from '../shared/labels';
import { TestingProgressBar } from '../shared/testing-progress';

const NOTE_MIN = 10;
const NOTE_MAX = 500;

/**
 * Programa de breeders: quem representa um banco de sementes (aprovado pela moderação) cadastra as sementes dele.
 * Elas ficam "em teste" e só entram no banco oficial quando 3 cultivadores de fora do banco as cultivam e há
 * uma avaliação pós-colheita. Quem representa um breeder nunca aparece publicamente.
 */
@Component({
  selector: 'app-breeder-program-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CatalogPicker, Icon, TestingProgressBar],
  template: `
    <a routerLink="/strains" class="text-sm text-fg-muted">← Catálogo</a>
    <h1 class="mt-2 text-2xl font-bold">Programa de breeders</h1>
    <p class="text-fg-muted">Para bancos de sementes colocarem as genéticas deles no catálogo, validadas por quem cultiva.</p>

    <ol class="wd-card mt-4 grid gap-2 text-sm">
      <li><strong>1. Peça para representar o banco.</strong> A moderação confere o vínculo (ex.: pelo site oficial). Ninguém mais vê seu pedido.</li>
      <li><strong>2. Cadastre as sementes.</strong> Elas entram como <em>em teste</em>: aparecem para quem for abrir um diário, mas ainda não no catálogo oficial.</li>
      <li>
        <strong>3. A comunidade valida.</strong> Com 3 cultivadores de fora do banco e 1 avaliação pós-colheita, a semente entra no
        banco oficial e no <a routerLink="/dados-abertos" class="text-accent underline">banco aberto</a>. Diários e avaliações de
        representantes do banco não contam.
      </li>
    </ol>

    @if (program(); as state) {
      @if (state.error) {
        <p role="alert" class="wd-card mt-4 text-danger">{{ state.error }}</p>
      }

      <section class="mt-6" aria-labelledby="representations-title">
        <h2 id="representations-title" class="text-lg font-semibold">Bancos que você representa</h2>
        <ul class="mt-3 grid gap-2">
          @for (rep of state.data.representations; track rep.breederId) {
            <li class="wd-card flex flex-wrap items-center gap-3">
              <span class="min-w-0 flex-1">
                <a [routerLink]="['/bancos', rep.breederId]" class="font-semibold">{{ rep.breeder }}</a>
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
            </li>
          } @empty {
            <li class="wd-card text-sm text-fg-muted">Você ainda não representa nenhum banco.</li>
          }
        </ul>

        <form class="wd-card mt-3 grid gap-3" (ngSubmit)="request()" aria-labelledby="request-title">
          <h3 id="request-title" class="font-semibold">Pedir para representar um banco</h3>
          <app-catalog-picker kind="breeders" label="Banco de sementes" placeholder="Busque o banco no catálogo" [(value)]="breeder" />
          <div>
            <label class="wd-label" for="rep-note">Como comprovar o vínculo (só a moderação vê)</label>
            <textarea id="rep-note" rows="3" class="wd-input py-2 text-base" [attr.minlength]="noteMin" [attr.maxlength]="noteMax"
                      placeholder="Ex.: sou o fundador; o site oficial do banco cita meu apelido na página Sobre."
                      [(ngModel)]="note" name="note"></textarea>
            <p class="mt-1 text-xs text-fg-muted">Sem telefone, PIX ou contato para negócio. A moderação pode pedir mais informações.</p>
          </div>
          <button type="submit" class="wd-button w-fit" [disabled]="busy() || !breeder() || note.trim().length < noteMin">Enviar pedido</button>
        </form>
      </section>

      @if (approved().length) {
        <section class="mt-6" aria-labelledby="register-title">
          <h2 id="register-title" class="text-lg font-semibold">Cadastrar semente para teste</h2>
          <form class="wd-card mt-3 grid gap-3 sm:grid-cols-2" (ngSubmit)="register()">
            <div>
              <label class="wd-label" for="s-breeder">Banco</label>
              <select id="s-breeder" class="wd-input" [(ngModel)]="strainBreederId" name="breederId">
                <option [ngValue]="undefined" disabled>Escolha o banco</option>
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
            <div class="sm:col-span-2">
              <label class="wd-label" for="s-genetics">Genética (cruzamento)</label>
              <input id="s-genetics" class="wd-input" maxlength="120" placeholder="Ex.: Gelato 41 x Zkittlez" [(ngModel)]="traits.genetics" name="genetics" />
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
          <h2 id="mine-title" class="text-lg font-semibold">Sementes dos seus bancos</h2>
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
                <p class="text-xs text-fg-muted">{{ strain.breeder }} · cadastrada em {{ date(strain.createdAt) }}</p>
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

  protected readonly noteMin = NOTE_MIN;
  protected readonly noteMax = NOTE_MAX;
  protected readonly dominanceOptions = options(DOMINANCE_LABEL);
  protected readonly speedOptions = options(SPEED_LABEL);
  protected readonly seedOptions = options(SEED_KIND_LABEL);

  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly breeder = signal<CatalogItem | null>(null);
  protected note = '';
  protected strainBreederId: string | undefined;
  protected strainName = '';
  protected traits: { floweringType?: StrainTraits['floweringType']; dominance?: Dominance; floweringSpeed?: FloweringSpeed; genetics?: string } = {};
  protected readonly seedTypes = new Set<SeedKind>();
  protected flavorsText = '';
  protected effectsText = '';

  private readonly reload = signal(0);
  protected readonly program = toSignal(
    toObservable(this.reload).pipe(
      switchMap(() =>
        this.api.mine().pipe(
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
    this.run(this.api.requestRepresentation(breeder.id, this.note.trim()), () => {
      this.message.set(`Pedido enviado. A moderação vai conferir seu vínculo com ${breeder.name}.`);
      this.breeder.set(null);
      this.note = '';
    });
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
      this.message.set('Escolha o banco da semente.');
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
