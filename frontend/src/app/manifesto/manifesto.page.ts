import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon, IconName } from '../shared/icon';

interface Principle {
  readonly icon: IconName;
  readonly title: string;
  readonly points: readonly string[];
}

/**
 * O que o weediário promete e por quê. Cada frase aqui corresponde a algo que o código realmente faz;
 * mudar o comportamento do app exige mudar esta página junto.
 */
const PRINCIPLES: readonly Principle[] = [
  {
    icon: 'user',
    title: 'Cadastro mínimo, pode ser pseudônimo',
    points: [
      'Pra criar conta a gente pede só um apelido, que não precisa ser o seu nome, e uma senha. No banco a senha fica ' +
        'como hash: nem a gente consegue ler.',
      'E-mail é opcional e serve só pra redefinir a senha e avisar de mudanças na conta. Fica cifrado, não serve pra ' +
        'entrar e dá pra remover quando quiser.',
      'Nunca pedimos CPF, nome completo, RG, endereço ou telefone.',
      'Esqueceu a senha? Você anota 12 palavras no cadastro, geradas no seu navegador e nunca enviadas ao servidor, e ' +
        'recupera a conta com elas. O outro lado: sem e-mail e sem as palavras, ninguém devolve a conta — nem a gente.',
    ],
  },
  {
    icon: 'eyeOff',
    title: 'Sem logs, sem rastreador',
    points: [
      'Não guardamos registro de acesso: nem IP, nem aparelho, nem as páginas que você abre.',
      'Sem rastreador e sem cookie de terceiros. O único cookie é o da sua sessão, que só serve pra te manter logado.',
      'Diário privado é privado: só você vê, e nunca aparece em feed, perfil, ranking ou busca. Rankings, receitas, ' +
        'equipamentos e tratamento são só pra quem tem conta.',
      'A única publicidade: uma marca pode pagar pra aparecer em destaque num ranking, sempre com a etiqueta de anúncio. ' +
        'Nenhum dado seu é usado ou vendido, e todo mundo vê o mesmo ranking. A área de tratamento não tem anúncio.',
      'Toda foto perde os metadados (GPS, data, modelo do celular) antes de ir pro ar. Reações e novos seguidores são anônimos.',
    ],
  },
  {
    icon: 'flag',
    title: 'Sem comércio, só cultivo',
    points: [
      'O weediário é pra registrar e trocar experiência. Venda, troca, doação ou contato pra negócio são proibidos.',
      'Um filtro bloqueia telefone, CPF, chave PIX e link de grupo antes de publicar, e segura pra revisão o que tem cara de anúncio.',
      'Qualquer um pode denunciar, de forma anônima. Com 3 denúncias o conteúdo sai do ar até a moderação decidir.',
    ],
  },
  {
    icon: 'pill',
    title: 'Saúde é assunto seu',
    points: [
      'O acompanhamento de tratamento é uma área separada, com consentimento próprio, e nunca aparece em feed, ranking ou ' +
        'perfil. Os textos vão cifrados pro banco.',
      'A gente só registra o que você usou e como se sentiu. Não indicamos dose, horário nem produto.',
      'Você pode baixar tudo ou apagar tudo quando quiser. Apagar é apagar de verdade.',
    ],
  },
];

/** Manifesto: o que acreditamos sobre privacidade e como o app foi construído pra isso. */
@Component({
  selector: 'app-manifesto-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <article class="mx-auto max-w-3xl">
      <header>
        <p class="text-sm font-semibold uppercase tracking-wider text-accent">Manifesto</p>
        <h1 class="mt-1 text-3xl font-bold">O seu cultivo e o seu tratamento são assunto seu.</h1>
        <p class="mt-3 text-lg text-fg-muted">
          O weediário existe pra registrar e trocar ideia sobre cannabis sem virar um arquivo sobre você. Privacidade
          aqui não é opção escondida nas configurações: é o jeito que o app foi construído.
        </p>
      </header>

      @for (principle of principles; track principle.title) {
        <section class="wd-card mt-6" [attr.aria-labelledby]="'principle-' + $index">
          <h2 [id]="'principle-' + $index" class="flex items-center gap-3 text-xl font-semibold">
            <span class="grid size-10 shrink-0 place-items-center rounded-xl bg-accent/15 text-accent">
              <app-icon [name]="principle.icon" class="size-5" />
            </span>
            {{ principle.title }}
          </h2>
          <ul class="mt-4 grid gap-3">
            @for (point of principle.points; track point) {
              <li class="flex gap-3">
                <app-icon name="check" class="mt-0.5 size-5 shrink-0 text-accent" />
                <span>{{ point }}</span>
              </li>
            }
          </ul>
        </section>
      }

      <p class="mt-6">
        As regras completas estão nos <a routerLink="/termos" class="font-semibold text-accent">Termos de Uso</a>.
      </p>

      <section class="mt-6 rounded-2xl border border-line p-5 text-sm text-fg-muted" aria-labelledby="honesty">
        <h2 id="honesty" class="font-semibold text-fg">Sendo honestos</h2>
        <p class="mt-2">
          Como em qualquer site, a sua operadora e quem hospeda o servidor enxergam as conexões passando. O que depende
          da gente é não guardar: no servidor fica só o que o app precisa pra funcionar, sem log de acesso e sem
          rastreador. Se algo neste manifesto deixar de ser verdade, ele muda antes, e você fica sabendo por aqui.
        </p>
      </section>

      <p class="mt-8 text-center">
        <a routerLink="/diarios" class="font-semibold text-accent">Bora ver os diários da comunidade →</a>
      </p>
    </article>
  `,
})
export class ManifestoPage {
  protected readonly principles = PRINCIPLES;
}
