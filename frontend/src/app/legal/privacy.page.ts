import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Section {
  readonly id: string;
  readonly title: string;
  readonly paragraphs: readonly string[];
}

const CONTACT = 'bruno.dev@weediario.com';

/**
 * Política de Privacidade (LGPD). Fica no código, como os Termos: o histórico do repositório prova o que cada versão
 * dizia. Só descreve o que o app faz de verdade; mudou o tratamento, muda o texto.
 */
export const PRIVACY_SECTIONS: readonly Section[] = [
  {
    id: 'quem-somos',
    title: '1. Quem somos e como falar com a gente',
    paragraphs: [
      'O weediário é operado por Bruno Dev Consultoria, CNPJ 55.670.379/0001-39, com sede no Rio de Janeiro/RJ, que é a ' +
        'controladora dos dados.',
      `Encarregado pelo tratamento de dados (DPO): Bruno Gonçalves, contato ${CONTACT}.`,
      `Privacidade: ${CONTACT} · Notificações extrajudiciais: ${CONTACT}.`,
    ],
  },
  {
    id: 'o-que-tratamos',
    title: '2. O que tratamos e por quanto tempo',
    paragraphs: [
      'Conta: apelido (pode ser pseudônimo), senha guardada só como hash Argon2id e o hash de um código derivado da frase ' +
        'de recuperação. Não pedimos nome, CPF, RG, endereço nem telefone.',
      'E-mail, se você cadastrar: guardado cifrado, usado só para confirmar o endereço, redefinir a senha e avisar de ' +
        'mudanças na conta.',
      'O que você publica: diários, fotos (sem GPS, data nem modelo do celular, removidos antes de ir para o ar), ' +
        'comentários, avaliações e receitas, com a visibilidade que você escolher.',
      'Acompanhamento de tratamento: o que você registra sobre doses, sintomas e efeitos, com os textos cifrados no banco.',
      'Registros da conta: versão e data do aceite dos Termos, eventos de segurança (tipo e data, por 90 dias) e registros ' +
        'de moderação (por 5 anos, para atender ordem judicial).',
      'Cookies: dois, ambos necessários. Um mantém a sessão (até 90 dias desde o login); o outro, por até 180 dias, lembra ' +
        'que o aparelho já entrou na conta, para o limite de tentativas de senha valer à parte para ele; esse ' +
        'não fica guardado no servidor.',
      'Só neste aparelho, sem ir para o servidor: o tema que você escolheu e, no guia de cultivo, as etapas que você marcou ' +
        'e a última que abriu. Ficam no armazenamento do navegador (localStorage), não são enviados a ninguém e saem quando ' +
        'você usa "Limpar o meu progresso" ou apaga os dados do site no navegador.',
      'Tudo isso fica enquanto a conta existir; ao apagar a conta, sai junto, salvo os registros de moderação e os itens ' +
        'que você cadastrou no catálogo aberto, que ficam sem vínculo com você. Cópias de backup somem em até 30 dias.',
    ],
  },
  {
    id: 'bases-legais',
    title: '3. Bases legais',
    paragraphs: [
      'Tratamos seus dados com base em: (i) execução de contrato (LGPD, art. 7º, V) para conta, diários e ferramentas; ' +
        '(ii) consentimento específico e destacado (art. 11, I) para os dados de saúde do acompanhamento de tratamento, que ' +
        'você pode retirar a qualquer momento; (iii) cumprimento de obrigação legal (art. 7º, II) para registros de ' +
        'moderação e atendimento a ordens judiciais; (iv) legítimo interesse (art. 7º, IX) apenas para a segurança da ' +
        'plataforma.',
    ],
  },
  {
    id: 'direitos',
    title: '4. Seus direitos',
    paragraphs: [
      'Você pode, a qualquer momento, pedir confirmação, acesso, correção, anonimização, bloqueio ou eliminação, ' +
        'portabilidade, informação sobre compartilhamento e revogação de consentimento.',
      'Baixar todos os seus dados e apagar a conta você faz sozinho, no seu perfil. Para o resto, respondemos em até 15 ' +
        `dias pelo e-mail ${CONTACT}. Você também pode peticionar à ANPD.`,
    ],
  },
  {
    id: 'compartilhamento',
    title: '5. Com quem compartilhamos',
    paragraphs: [
      'Hospedagem: o servidor do weediário fica na Hostinger, no Brasil. O banco de dados e as fotos ficam nele, inclusive ' +
        'o conteúdo dos diários; o provedor guarda os arquivos, mas não usa esses dados.',
      'E-mail: quando você cadastra um e-mail, as mensagens da conta saem pelo serviço de e-mail da Hostinger, que recebe ' +
        'só o endereço e a mensagem. Se esse envio passar por servidores fora do Brasil, a transferência segue o art. 33 da ' +
        'LGPD, nas garantias contratuais do provedor.',
      'Backup: as cópias de segurança são cifradas antes de sair do servidor; quem as guarda não consegue lê-las.',
      'Vídeos do guia de cultivo: as miniaturas ficam no nosso servidor. O vídeo só vem do YouTube (endereço ' +
        'youtube-nocookie.com) se você clicar nele; nesse momento o YouTube recebe o seu IP e o endereço do site (só o domínio, sem a página), ' +
        'e vale a política dele. Se não clicar, nada é enviado.',
      'Não vendemos dados, não usamos ferramentas de estatística nem cookies de terceiros, e nenhum anunciante recebe dados ' +
        'seus.',
    ],
  },
  {
    id: 'seguranca',
    title: '6. Segurança e incidentes',
    paragraphs: [
      'Adotamos HTTPS em todo o site, Argon2id para senhas, cifra dos textos de saúde, dos e-mails e dos backups, e acesso ' +
        'ao servidor restrito por função.',
      'Se ocorrer incidente relevante, comunicamos você (por aviso no site e, se você cadastrou e-mail, por e-mail) e a ANPD ' +
        'nos prazos definidos pela ANPD (LGPD, art. 48).',
    ],
  },
  {
    id: 'decisoes-automatizadas',
    title: '7. Decisões automatizadas',
    paragraphs: [
      'Um filtro automático bloqueia textos com telefone, documento, chave PIX ou link de mensageiro e retém para revisão ' +
        'textos com cara de anúncio; três denúncias tiram um conteúdo do ar até a moderação decidir.',
      'Quando uma decisão automatizada bloquear ou retirar do ar um conteúdo seu, você pode pedir revisão humana em até 10 ' +
        `dias, apresentando a sua versão dos fatos, pelo e-mail ${CONTACT} (LGPD, art. 20). Se o conteúdo ficou retido ` +
        'aguardando a moderação, você também pode mandar a sua versão pelo aviso nas suas notificações, em até 5 dias.',
    ],
  },
  {
    id: 'menores',
    title: '8. Menores de 18',
    paragraphs: [
      'O weediário é só para maiores de 18 anos. Se identificarmos conta de menor de 18 anos, ela é encerrada e os dados ' +
        'são eliminados (LGPD, art. 14).',
    ],
  },
];

/** Política de Privacidade: pública, linkada no cadastro, nos Termos e no rodapé. */
@Component({
  selector: 'app-privacy-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <article class="mx-auto max-w-3xl">
      <h1 class="text-3xl font-bold">Política de Privacidade</h1>
      <p class="mt-2 text-fg-muted">
        Como o weediário trata os seus dados, com base na Lei Geral de Proteção de Dados (Lei nº
        13.709/2018). As regras de uso da comunidade estão nos
        <a routerLink="/termos" class="text-accent underline">Termos de Uso</a>, e o porquê de cada
        escolha, no <a routerLink="/manifesto" class="text-accent underline">Manifesto</a>.
      </p>

      <nav aria-label="Seções" class="wd-card mt-6">
        <ol class="grid gap-1 text-sm">
          @for (section of sections; track section.id) {
            <li>
              <button type="button" class="text-left text-accent" (click)="scrollTo(section.id)">
                {{ section.title }}
              </button>
            </li>
          }
        </ol>
      </nav>

      @for (section of sections; track section.id) {
        <section [id]="section.id" class="mt-8 scroll-mt-24">
          <h2 class="text-lg font-semibold">{{ section.title }}</h2>
          @for (paragraph of section.paragraphs; track $index) {
            <p class="mt-2 text-fg-muted">{{ paragraph }}</p>
          }
        </section>
      }
    </article>
  `,
})
export class PrivacyPage {
  protected readonly sections = PRIVACY_SECTIONS;

  protected scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
