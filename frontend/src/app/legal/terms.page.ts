import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';

/** Versão vigente. Mudou o texto, muda a versão (e o backend também): todo mundo aceita de novo antes de publicar. */
export const TERMS_VERSION = '1.0';

/** "2026-11-01" → "1º de novembro de 2026". */
function formatLongDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  const months = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro',
    'novembro', 'dezembro'];
  return `${day === 1 ? '1º' : day} de ${months[month - 1]} de ${year}`;
}

interface Clause {
  readonly id: string;
  readonly title: string;
  readonly paragraphs: readonly string[];
  readonly highlight?: boolean;
}

/**
 * Texto dos Termos de Uso. Fica no código: o histórico do repositório prova exatamente o que cada versão dizia; no
 * banco vai só a versão aceita e a data/hora do aceite.
 */
export const CLAUSES: readonly Clause[] = [
  {
    id: 'o-que-e',
    title: '1. O que é o weediário',
    paragraphs: [
      'O weediário é uma ferramenta de diário pessoal de cultivo e de acompanhamento de tratamento com cannabis medicinal, ' +
        'com uma comunidade para trocar experiências. Não é loja, marketplace nem classificados.',
      'Você é responsável por cumprir a lei que se aplica ao seu cultivo e ao seu tratamento (por exemplo: autorização ' +
        'judicial, vínculo com associação ou prescrição).',
    ],
  },
  {
    id: 'idade',
    title: '2. Quem pode usar',
    paragraphs: ['Só pessoas com 18 anos ou mais.'],
  },
  {
    id: 'dados',
    title: '3. Sua conta e seus dados',
    paragraphs: [
      'Pedimos um apelido (pode ser pseudônimo) e uma senha escolhida por você. O e-mail é opcional. Não pedimos CPF, nome completo, RG, ' +
        'endereço ou telefone.',
      'A senha é guardada só como hash (Argon2id): ninguém da equipe consegue lê-la. Para recuperar a conta, toda conta ' +
        'tem uma frase de 12 palavras, mostrada no cadastro; dela guardamos só o hash de um código derivado, então não ' +
        'temos como ver ou reenviar a frase.',
      'Se você cadastrar um e-mail, ele é usado só para confirmar o endereço, redefinir a senha e avisar de mudanças ' +
        'sensíveis na conta. Ele fica cifrado, nunca aparece para ninguém, não serve para entrar nem para procurar contas, ' +
        'e você pode removê-lo quando quiser. Para entregar esses e-mails usamos um provedor de envio, que recebe o ' +
        'endereço e a mensagem. Sem e-mail cadastrado, se você esquecer a senha e perder a frase, a conta não pode ser ' +
        'recuperada.',
      'Como exige o Marco Civil da Internet (Lei nº 12.965/2014, art. 15), guardamos os registros de acesso ao app ' +
        '(endereço IP, data e hora) por 6 meses, sob sigilo, e depois os apagamos. Eles só são fornecidos mediante ' +
        'ordem judicial.',
      'Mudanças sensíveis na conta (senha, e-mail, frase) ficam registradas por 90 dias só com o tipo e a data, para ' +
        'você conferir no perfil. Do aceite destes termos, guardamos só a versão e a data e hora. Os textos do ' +
        'acompanhamento de tratamento ficam cifrados. Os detalhes estão no Manifesto.',
      'No seu perfil você pode baixar todos os seus dados (um .zip com os dados e as fotos) e apagar a conta quando ' +
        'quiser; os dois pedem a sua senha. Ao apagar, somem a conta, os diários, as fotos, as receitas, as avaliações, ' +
        'os comentários e o tratamento. Ficam, sem vínculo com você: os itens que você cadastrou no catálogo aberto, os ' +
        'registros de moderação sobre a conta (guardados por 5 anos, para atender ordem judicial) e as reações e ' +
        'denúncias, que já são anônimas.',
    ],
  },
  {
    id: 'cookies',
    title: '4. Cookies, estatísticas e anúncios',
    paragraphs: [
      'O weediário exibe anúncios e mede o uso do site com o Google Analytics, ambos fornecidos pelo Google. Para isso, ' +
        'o Google usa cookies e recebe dados do seu navegador, como endereço IP, aparelho, páginas visitadas e ' +
        'interações com os anúncios, e pode tratá-los fora do Brasil, conforme a política de privacidade dele ' +
        '(policies.google.com/privacy).',
      'Esses cookies só são ativados se você aceitar no aviso de cookies. Você pode recusar ou mudar a escolha a ' +
        'qualquer momento, sem perder acesso a nenhuma função do app.',
    ],
  },
  {
    id: 'sem-comercio',
    title: '5. Proibição absoluta de comércio entre usuários',
    highlight: true,
    paragraphs: [
      'É estritamente proibido usar o weediário para ofertar, negociar, vender, comprar, trocar, doar, intermediar ou ' +
        'divulgar meios de contato (como links de Telegram ou WhatsApp, telefones, chaves PIX ou endereços) para o comércio ' +
        'de substâncias ilícitas, medicamentos controlados, estacas, clones, sementes ou derivados.',
      'O weediário é uma ferramenta exclusiva de diário pessoal e de acompanhamento agronômico e medicinal.',
      'Para ajudar a cumprir esta regra, um filtro automático bloqueia telefones, documentos, chaves PIX e links de ' +
        'mensageiros, e segura para revisão da moderação textos com cara de anúncio.',
    ],
  },
  {
    id: 'responsabilidade',
    title: '6. Responsabilidade pelo conteúdo',
    paragraphs: [
      'O weediário opera como provedor de aplicação de internet, nos termos da Lei nº 12.965/2014 (Marco Civil da Internet). ' +
        'Todo conteúdo, nota ou publicação inserida na plataforma é de responsabilidade civil e criminal exclusiva de quem o criou.',
    ],
  },
  {
    id: 'moderacao',
    title: '7. Moderação, suspensão e banimento',
    paragraphs: [
      'A plataforma pode remover conteúdos e suspender ou encerrar definitivamente contas que violarem estes termos, sem ' +
        'aviso prévio e sem indenização, a partir de constatação automatizada ou de denúncia.',
      'Qualquer pessoa pode denunciar um conteúdo, de forma anônima. Com três denúncias, o conteúdo sai do ar até a ' +
        'moderação decidir.',
      'Cada decisão da moderação fica registrada por 5 anos com data, conteúdo, conta, motivo e um resumo criptográfico ' +
        '(hash) do texto removido. Esse registro não guarda IP, não guarda quem denunciou e não guarda o conteúdo em si.',
    ],
  },
  {
    id: 'autoridades',
    title: '8. Cooperação com autoridades',
    paragraphs: [
      'O weediário colabora com as autoridades judiciais e policiais mediante ordem judicial fundamentada, nos termos da ' +
        'legislação brasileira vigente.',
      'Nesse caso, entregamos os dados que a ordem pedir, entre os que de fato mantemos: apelido, conteúdos ' +
        'publicados, registro de moderação e os registros de acesso dos últimos 6 meses.',
    ],
  },
  {
    id: 'saude',
    title: '9. Acompanhamento de tratamento',
    paragraphs: [
      'A área de tratamento só registra o que você usou e como se sentiu. O weediário não indica dose, horário nem produto, ' +
        'e não substitui a orientação do profissional que acompanha o tratamento.',
    ],
  },
  {
    id: 'mudancas',
    title: '10. Mudanças nestes termos',
    paragraphs: [
      'Se o texto mudar, a versão muda junto, e você precisa ler e aceitar a nova versão antes de voltar a publicar. ' +
        'Enquanto isso, sua conta continua podendo ler, baixar e apagar seus dados.',
    ],
  },
];

/** Termos de Uso: leitura pública e, para quem está com a versão antiga, o novo aceite. */
@Component({
  selector: 'app-terms-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <article class="mx-auto max-w-3xl">
      <header>
        <p class="text-sm font-semibold uppercase tracking-wider text-accent">Termos de Uso</p>
        <h1 class="mt-1 text-3xl font-bold">As regras do weediário</h1>
        <p class="mt-2 text-sm text-fg-muted">
          Versão {{ version }}@if (publishedOn(); as date) { · publicada em {{ date }}}
        </p>
      </header>

      @if (auth.needsTermsAcceptance()) {
        <p class="mt-4 rounded-xl border border-warn/50 bg-warn/10 p-3 text-sm" role="status">
          Atualizamos os Termos de Uso. Leia e aceite a versão {{ version }} no fim da página para continuar publicando.
        </p>
      }

      <nav class="mt-4 text-sm" aria-label="Seções">
        <ul class="flex flex-wrap gap-x-3 gap-y-1">
          @for (clause of clauses; track clause.id) {
            <li><button type="button" class="text-left text-accent" (click)="scrollTo(clause.id)">{{ clause.title }}</button></li>
          }
        </ul>
      </nav>

      @for (clause of clauses; track clause.id) {
        <section [id]="clause.id" class="mt-6 scroll-mt-24 rounded-2xl p-4"
                 [class]="clause.highlight ? 'border-2 border-danger/50 bg-danger/5' : 'border border-line bg-surface'">
          <h2 class="text-lg font-semibold">{{ clause.title }}</h2>
          @for (paragraph of clause.paragraphs; track $index) {
            <p class="mt-2">{{ paragraph }}</p>
          }
        </section>
      }

      <p class="mt-6 text-sm text-fg-muted">
        Veja também o <a routerLink="/manifesto" class="text-accent">Manifesto</a>.
      </p>

      @if (auth.needsTermsAcceptance()) {
        <section class="wd-card mt-6 grid gap-3" aria-labelledby="accept-title">
          <h2 id="accept-title" class="font-semibold">Aceitar a versão {{ version }}</h2>
          <label class="flex items-start gap-2 text-sm">
            <input type="checkbox" class="mt-0.5 size-5" [checked]="agreed()" (change)="agreed.set($any($event.target).checked)" />
            Li e aceito os Termos de Uso, incluindo a proibição de comércio.
          </label>
          @if (error()) {
            <p role="alert" class="text-sm text-danger">{{ error() }}</p>
          }
          <button type="button" class="wd-button w-fit" [disabled]="!agreed() || busy()" (click)="accept()">Aceitar e continuar</button>
        </section>
      }
    </article>
  `,
})
export class TermsPage {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly clauses = CLAUSES;
  protected readonly version = TERMS_VERSION;
  /** Data de publicação vem do servidor (definida no lançamento); sem ela, a página não mostra data. */
  protected readonly publishedOn = toSignal(
    inject(HttpClient).get<{ version: string; publishedOn?: string }>('/api/v1/terms').pipe(
      map((terms) => (terms.publishedOn ? formatLongDate(terms.publishedOn) : null)),
      catchError(() => of(null)),
    ),
    { initialValue: null },
  );
  protected readonly agreed = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected async accept(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.auth.acceptTerms(TERMS_VERSION);
      await this.router.navigateByUrl('/diarios');
    } catch (error) {
      this.error.set(problemMessage(error, 'Não foi possível registrar o aceite.'));
    } finally {
      this.busy.set(false);
    }
  }
}
