import { SITE_NAME, SITE_URL } from './public-pages';

const GITHUB_URL = 'https://github.com/canabisoft/weediario';

const ORGANIZATION = {
  '@type': 'Organization',
  name: SITE_NAME,
  url: SITE_URL,
  logo: `${SITE_URL}/icons/icon-512x512.png`,
  sameAs: [GITHUB_URL],
};

/** Mesmo texto das respostas que a landing e o manifesto já dão: nada que o app não faça. */
const FAQ: readonly { question: string; answer: string }[] = [
  {
    question: 'O weediário é gratuito?',
    answer:
      'Sim. Criar conta, manter diários e usar as ferramentas não custa nada, e não existe plano pago. O código é aberto no GitHub, para qualquer pessoa conferir o que acontece com os dados.',
  },
  {
    question: 'O weediário pede meus dados?',
    answer:
      'Não. O cadastro é só um apelido e uma senha; nunca pedimos nome, CPF, telefone ou endereço. O e-mail é opcional, serve só para recuperar a senha e fica cifrado.',
  },
  {
    question: 'Meus diários são públicos?',
    answer:
      'Não para a internet. Você escolhe entre comunidade, que só quem tem conta vê, e privado, que só você vê. Diários, fotos e perfis nunca aparecem para visitantes nem em buscadores.',
  },
  {
    question: 'O weediário vende ou troca sementes?',
    answer:
      'Não. O weediário é para registrar e trocar experiência de cultivo; venda, troca e doação entre usuários são proibidas. Um filtro bloqueia telefone, CPF, chave PIX e link de grupo antes de publicar.',
  },
];

const HOME = [
  {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: SITE_NAME,
    url: `${SITE_URL}/`,
    inLanguage: 'pt-BR',
    potentialAction: {
      '@type': 'SearchAction',
      target: { '@type': 'EntryPoint', urlTemplate: `${SITE_URL}/strains?q={search_term_string}` },
      'query-input': 'required name=search_term_string',
    },
  },
  { '@context': 'https://schema.org', ...ORGANIZATION },
  {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: FAQ.map((item) => ({
      '@type': 'Question',
      name: item.question,
      acceptedAnswer: { '@type': 'Answer', text: item.answer },
    })),
  },
];

/** O catálogo agregado, sem nenhum dado de usuário (ver OpenDataController no backend). */
const STRAINS = [
  {
    '@context': 'https://schema.org',
    '@type': 'Dataset',
    name: 'Catálogo aberto de strains do weediário',
    description:
      'Genéticas de cannabis com bancos de sementes, características e as médias de avaliação e de rendimento registradas pela comunidade. Só números agregados, sem dado pessoal de ninguém.',
    url: `${SITE_URL}/strains`,
    inLanguage: 'pt-BR',
    isAccessibleForFree: true,
    license: 'https://opendatacommons.org/licenses/odbl/1-0/',
    creator: ORGANIZATION,
    distribution: [
      {
        '@type': 'DataDownload',
        encodingFormat: 'text/csv',
        contentUrl: `${SITE_URL}/api/v1/open-data/strains.csv`,
      },
      {
        '@type': 'DataDownload',
        encodingFormat: 'application/json',
        contentUrl: `${SITE_URL}/api/v1/open-data/strains`,
      },
    ],
  },
];

/** JSON-LD de cada página pública; as outras não têm. */
export function structuredDataFor(path: string): readonly object[] {
  switch (path) {
    case '/':
      return HOME;
    case '/strains':
      return STRAINS;
    default:
      return [];
  }
}
