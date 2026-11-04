import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon, IconName } from '../shared/icon';

interface Feature {
  readonly icon: IconName;
  readonly title: string;
  readonly text: string;
}

const FEATURES: readonly Feature[] = [
  {
    icon: 'camera',
    title: 'Diário por semana',
    text: 'Fase, fotos, rega, pH e altura de cada update, da germinação à cura. O VPD sai calculado na hora.',
  },
  {
    icon: 'database',
    title: '8.488 genéticas no catálogo aberto',
    text: 'Veja como cada genética se saiu em cultivos de verdade: notas pós-colheita e gramas por planta.',
  },
  {
    icon: 'bulb',
    title: 'Ferramentas que funcionam offline',
    text: 'Calculadora de VPD e conversor EC/PPM dentro da tenda, mesmo sem sinal.',
  },
  {
    icon: 'pill',
    title: 'Acompanhamento de tratamento',
    text: 'Para quem usa cannabis medicinal: doses e sintomas numa área separada, com textos cifrados.',
  },
];

const NEVER: readonly Feature[] = [
  {
    icon: 'lock',
    title: 'Nome, CPF, RG, endereço e telefone',
    text: 'Nunca pedimos. O cadastro é apelido e senha.',
  },
  {
    icon: 'eyeOff',
    title: 'IP, aparelho e páginas que você abre',
    text: 'Não associamos registro de acesso à sua conta. Sem rastreador e sem cookie de terceiros.',
  },
  {
    icon: 'image',
    title: 'GPS, data e modelo do celular nas fotos',
    text: 'Toda foto é redesenhada do zero antes de ir para o ar, sem nenhum metadado.',
  },
  {
    icon: 'shield',
    title: 'Seus dados, para sempre',
    text: 'Baixe tudo ou apague tudo quando quiser. Apagar é apagar de verdade.',
  },
];

interface OpenPage {
  readonly path: string;
  readonly icon: IconName;
  readonly title: string;
  readonly text: string;
}

/** Visitante não tem menu lateral: as páginas que abrem sem conta ficam linkadas aqui. */
const OPEN_PAGES: readonly OpenPage[] = [
  {
    path: '/strains',
    icon: 'leaf',
    title: 'Catálogo de strains',
    text: 'Genéticas, bancos de sementes e produtos, com as médias de avaliação da comunidade.',
  },
  {
    path: '/dados-abertos',
    icon: 'database',
    title: 'Banco aberto',
    text: 'Os números do catálogo para baixar em CSV ou JSON, sem dado pessoal de ninguém.',
  },
  {
    path: '/breeders/programa',
    icon: 'sprout',
    title: 'Programa de breeders',
    text: 'Para bancos e breeders independentes cadastrarem genéticas validadas pela comunidade.',
  },
  {
    path: '/ferramentas',
    icon: 'tools',
    title: 'Ferramentas',
    text: 'Calculadora de VPD e conversor EC/PPM, que funcionam até sem internet.',
  },
];

/**
 * Home de quem ainda não tem conta: diz o que é o weediário, o que ele faz e o que ele nunca pede. Comunidade
 * fechada, então nada aqui vem da API: é tudo texto fixo e a foto de exemplo, servida do próprio domínio.
 * <p>
 * Foto: "Cannabis bloom", de Rob Duval, no Wikimedia Commons, em domínio público (CC0); convertida para WebP sem
 * metadados.
 */
@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <section
      class="grid items-center gap-10 py-4 md:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)] md:py-10"
    >
      <div>
        <h1 class="text-4xl font-extrabold leading-[1.05] tracking-tight text-fg sm:text-5xl">
          O seu cultivo e o seu tratamento são assuntos seus.
        </h1>
        <p class="mt-5 max-w-xl text-lg text-fg-muted">
          O weediário é um diário de cultivo de cannabis para quem planta em casa. Você anota cada
          semana da planta, troca experiência com outros growers e não entrega nome, CPF nem
          telefone para ninguém.
        </p>
        <div class="mt-7 flex flex-wrap gap-3">
          <a routerLink="/cadastro" class="wd-button px-6">Criar conta grátis</a>
          <a
            routerLink="/manifesto"
            class="wd-button border border-line bg-transparent px-6 text-fg"
            >Ler o manifesto</a
          >
        </div>
        <p class="mt-3 text-sm text-fg-muted">Só um apelido e uma senha. Sem e-mail obrigatório.</p>
        <p class="mt-1 text-sm text-fg-muted">
          Gratuito e sem plano pago. O código é aberto e você pode exportar ou apagar tudo.
        </p>
      </div>

      <figure
        class="overflow-hidden rounded-3xl border border-line bg-surface"
        aria-label="Exemplo de update de diário"
      >
        <img
          src="landing/flor-460.webp"
          srcset="landing/flor-460.webp 460w, landing/flor-920.webp 920w"
          sizes="(min-width: 768px) 460px, 100vw"
          width="920"
          height="690"
          fetchpriority="high"
          alt="Flor feminina de cannabis em floração, com tricomas e pistilos"
          class="aspect-[4/3] w-full object-cover"
        />
        <figcaption class="grid gap-3 p-5">
          <span class="flex flex-wrap items-center gap-2 text-xs font-semibold">
            <span class="rounded-lg bg-accent/15 px-2.5 py-1 text-accent">Flora</span>
            <span class="rounded-lg bg-surface-raised px-2.5 py-1">Semana 6 · dia 3</span>
            <span class="ml-auto inline-flex items-center gap-1 font-medium text-fg-muted">
              <app-icon name="lock" class="size-3.5" /> Privado
            </span>
          </span>
          <span class="text-sm text-fg-muted"
            >Pistilos aparecendo e os primeiros tricomas. Rega de 700 ml com pH 6,2.</span
          >
          <span class="grid grid-cols-3 gap-2">
            <span class="rounded-xl bg-surface-raised px-3 py-2">
              <span class="block text-xs text-fg-muted">Temperatura</span
              ><span class="font-bold">25,5 °C</span>
            </span>
            <span class="rounded-xl bg-surface-raised px-3 py-2">
              <span class="block text-xs text-fg-muted">Umidade</span
              ><span class="font-bold">55 %</span>
            </span>
            <span class="rounded-xl bg-surface-raised px-3 py-2">
              <span class="block text-xs text-fg-muted">VPD</span
              ><span class="font-bold text-accent">1,10 kPa</span>
            </span>
          </span>
        </figcaption>
      </figure>
    </section>

    <section class="mt-12" aria-labelledby="landing-o-que">
      <h2 id="landing-o-que" class="max-w-2xl text-2xl font-extrabold tracking-tight sm:text-3xl">
        Tudo o que um grower anota no caderno, num lugar só.
      </h2>
      <ul class="mt-6 grid gap-3 sm:grid-cols-2">
        @for (feature of features; track feature.title) {
          <li class="wd-card p-5">
            <app-icon [name]="feature.icon" class="size-6 text-accent" />
            <h3 class="mt-3 font-semibold">{{ feature.title }}</h3>
            <p class="mt-1 text-sm text-fg-muted">{{ feature.text }}</p>
          </li>
        }
      </ul>
    </section>

    <section
      class="mt-16 grid gap-8 md:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]"
      aria-labelledby="landing-nunca"
    >
      <div>
        <h2 id="landing-nunca" class="text-2xl font-extrabold tracking-tight sm:text-3xl">
          O que a gente nunca pede, nem guarda.
        </h2>
        <p class="mt-4 text-fg-muted">
          Para a comunidade você é só o seu apelido. O resto não existe no banco de dados, então não
          tem como vazar.
        </p>
        <a routerLink="/manifesto" class="mt-5 inline-block font-semibold text-accent"
          >Ler o manifesto completo →</a
        >
      </div>
      <ul class="grid gap-3">
        @for (item of never; track item.title) {
          <li class="wd-card flex items-start gap-4 p-5">
            <app-icon [name]="item.icon" class="mt-0.5 size-5 shrink-0 text-accent" />
            <span>
              <span class="block font-semibold">{{ item.title }}</span>
              <span class="block text-sm text-fg-muted">{{ item.text }}</span>
            </span>
          </li>
        }
      </ul>
    </section>

    <section
      class="wd-card mt-16 grid gap-8 p-6 sm:p-10 md:grid-cols-2"
      aria-labelledby="landing-fechada"
    >
      <div>
        <h2 id="landing-fechada" class="text-2xl font-extrabold tracking-tight">
          Uma comunidade fechada, sem comércio.
        </h2>
        <p class="mt-3 text-fg-muted">
          Diários, fotos, perfis e avaliações só aparecem para quem tem conta: nada disso chega a
          visitantes nem a buscadores. E aqui é para trocar experiência, não para negociar.
        </p>
      </div>
      <dl class="grid gap-5">
        <div>
          <dt class="font-semibold">Venda, troca e doação são proibidas</dt>
          <dd class="mt-1 text-sm text-fg-muted">
            Um filtro bloqueia telefone, CPF, chave PIX e link de grupo antes de publicar.
          </dd>
        </div>
        <div>
          <dt class="font-semibold">Denúncia anônima</dt>
          <dd class="mt-1 text-sm text-fg-muted">
            Com 3 denúncias, o conteúdo sai do ar até a moderação decidir.
          </dd>
        </div>
        <div>
          <dt class="font-semibold">Diário privado é privado</dt>
          <dd class="mt-1 text-sm text-fg-muted">
            Só você vê. Não aparece em feed, perfil, ranking nem busca.
          </dd>
        </div>
      </dl>
    </section>

    <section class="mt-16" aria-labelledby="landing-aberto">
      <h2 id="landing-aberto" class="text-2xl font-extrabold tracking-tight sm:text-3xl">
        Estas páginas não pedem conta
      </h2>
      <p class="mt-2 text-fg-muted">Catálogo, banco aberto e ferramentas, para qualquer pessoa.</p>
      <ul class="mt-6 grid gap-3 sm:grid-cols-2">
        @for (page of openPages; track page.path) {
          <li>
            <a
              [routerLink]="page.path"
              class="wd-card flex h-full items-start gap-4 p-5 hover:border-accent/60"
            >
              <app-icon [name]="page.icon" class="mt-0.5 size-6 shrink-0 text-accent" />
              <span>
                <span class="block font-semibold text-fg">{{ page.title }}</span>
                <span class="mt-1 block text-sm text-fg-muted">{{ page.text }}</span>
              </span>
            </a>
          </li>
        }
      </ul>
    </section>

    <section class="mx-auto mt-16 max-w-2xl py-6 text-center" aria-labelledby="landing-comece">
      <h2 id="landing-comece" class="text-3xl font-extrabold tracking-tight">
        Comece o diário do seu próximo cultivo.
      </h2>
      <p class="mt-3 text-fg-muted">
        É de graça, o código é aberto e você leva menos de um minuto para criar a conta.
      </p>
      <div class="mt-6 flex flex-wrap justify-center gap-3">
        <a routerLink="/cadastro" class="wd-button px-6">Criar conta grátis</a>
        <a routerLink="/entrar" class="wd-button border border-line bg-transparent px-6 text-fg"
          >Já tenho conta</a
        >
      </div>
    </section>

    <footer
      class="mt-12 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-line pt-6 text-sm text-fg-muted"
    >
      <span class="mr-auto">
        Ferramenta de registro para maiores de 18 anos. Não incentiva nem intermedeia cultivo ou
        comércio ilegal.
      </span>
      <nav aria-label="Institucional" class="flex flex-wrap gap-x-6 gap-y-2">
        <a routerLink="/manifesto" class="hover:text-fg">Manifesto</a>
        <a routerLink="/termos" class="hover:text-fg">Termos de Uso</a>
        <a routerLink="/ajustes" class="hover:text-fg">Ajustes</a>
        <a
          href="https://github.com/canabisoft/weediario"
          target="_blank"
          rel="noopener noreferrer"
          class="hover:text-fg"
          >Código no GitHub</a
        >
      </nav>
    </footer>
  `,
})
export class Landing {
  protected readonly features = FEATURES;
  protected readonly never = NEVER;
  protected readonly openPages = OPEN_PAGES;
}
