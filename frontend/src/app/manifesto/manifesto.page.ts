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
      'Pra criar conta a gente pede um apelido, que não precisa ser o seu nome, e uma senha. No banco a senha fica só como hash: nem a gente consegue ler.',
      'E-mail é opcional. Se você quiser, ele serve só pra redefinir a senha e te avisar de mudanças na conta: fica cifrado, ' +
        'nunca aparece pra ninguém, não serve pra entrar e o cadastro nunca diz se um e-mail já tem conta. Dá pra remover quando quiser.',
      'Nunca pedimos CPF, nome completo, RG, endereço ou telefone. Não tem campo pra isso, nem escondido.',
      'Esqueceu a senha? Você anota 12 palavras na hora do cadastro (no padrão BIP-39, o mesmo das carteiras de cripto). ' +
        'Elas são geradas no seu navegador e nunca chegam ao servidor: a gente guarda só um hash de um código derivado delas. ' +
        'Com elas você cria uma senha nova (e ganha uma frase nova). O outro lado disso: sem e-mail, se você perder a senha e as palavras, ninguém consegue devolver a conta.',
      'Um limite honesto: como em todo site, o código que gera a frase no seu navegador é entregue pelo nosso servidor. ' +
        'Por isso cada arquivo do app vai com uma assinatura de integridade (SRI).',
    ],
  },
  {
    icon: 'eyeOff',
    title: 'Pouco rastro, e só o que a lei exige',
    points: [
      'O único registro de acesso que guardamos é o que o Marco Civil da Internet exige: IP, data e hora, por 6 meses, ' +
        'sob sigilo. Ele só sai daqui com ordem judicial e depois é apagado.',
      'No nosso banco não existe tabela de "visitas" nem de comportamento: não registramos o que você abre ou clica.',
      'O site se mantém com anúncios e usa o Google Analytics pra entender o uso. Os dois só ligam se você aceitar ' +
        'os cookies; recusou, o app funciona igual.',
      'A área de tratamento não mostra anúncio e não envia nada ao analytics.',
      'Toda foto passa por uma faxina antes de ir pro ar: GPS, data e modelo do celular saem dos metadados.',
      'Reações e novos seguidores são anônimos, até pra quem recebe. Notificações somem sozinhas em 90 dias.',
    ],
  },
  {
    icon: 'flag',
    title: 'Sem comércio, só cultivo',
    points: [
      'O weediário é pra registrar e trocar experiência. Venda, troca, doação ou contato pra negócio são proibidos, sem exceção.',
      'Um filtro bloqueia telefone, CPF, chave PIX e link de grupo antes de publicar, e segura pra revisão o que tem cara de anúncio.',
      'Qualquer um pode denunciar, de forma anônima. Com 3 denúncias o conteúdo sai do ar até a moderação decidir.',
      'Toda decisão da moderação fica registrada, sem IP e sem quem denunciou. Isso protege você e o projeto.',
    ],
  },
  {
    icon: 'pill',
    title: 'Saúde é assunto seu',
    points: [
      'O acompanhamento de tratamento é uma área separada, com consentimento próprio, e nunca aparece em feed, ranking ou perfil.',
      'Os textos de saúde vão pro banco cifrados.',
      'A gente só registra o que você usou e como se sentiu. Não indicamos dose, horário nem produto: isso é com quem acompanha o seu tratamento.',
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
          Como em qualquer site, a sua operadora de internet e a empresa que hospeda o servidor enxergam as conexões
          passando, e, se você aceitar os cookies, o Google também vê as suas visitas. O que depende da gente é guardar
          só o que a lei obriga, pelo tempo que ela obriga. Se algum dia algo neste manifesto deixar de ser verdade, ele
          muda antes, e você fica sabendo por aqui.
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
