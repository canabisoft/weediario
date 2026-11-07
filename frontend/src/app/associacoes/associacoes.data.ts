/**
 * Associações de pacientes de cannabis medicinal, só com o que é público e institucional: nome, cidade, estado, site,
 * logotipo e canais de contato da própria associação. Sobre produtos, só a CATEGORIA genérica que a própria associação diz
 * ter no catálogo (óleo, pomada...), atribuída a ela: sem nome de produto, imagem, concentração nem preço (a lei restringe
 * a divulgação de produtos). O contato sai com a mensagem "vim do weediario.com", em link direto, sem redirecionador nem contagem.
 * Origem: levantamento da Central da Cannabis (api/data/seed/associacoes.json), de 06/11/2026, a partir dos sites
 * de cada uma. Confirme sempre com a associação. O weediário não recebe comissão nem contato de ninguém por esta lista.
 */
export interface Associacao {
  readonly slug: string;
  readonly nome: string;
  readonly cidade: string | null;
  readonly uf: string;
  readonly site: string;
  /** Envia ou atende pessoas de outros estados: true confirmado; null = não confirmado. */
  readonly outrosEstados: boolean | null;
  readonly resumo: string | null;
  /** Arquivo em public/associacoes/ (só com a autorização da associação para usar a imagem). */
  readonly logo: string | null;
  /** WhatsApp da associação, 55 + DDD + número (só dígitos). null = ainda não temos. */
  readonly whatsapp: string | null;
  readonly email: string | null;
  /** Texto como a associação divulga, ex.: '0800 313 2367'. */
  readonly telefone: string | null;
  /** Categorias genéricas que a associação informa ter no catálogo dela. Vazio = catálogo ainda não levantado. */
  readonly categorias: readonly Categoria[];
}

export type Categoria = 'oleo' | 'flor' | 'pomada' | 'creme' | 'spray' | 'goma';

/** Só a forma do produto, em termos genéricos. Nada de marca, concentração ou preço. */
export const CATEGORIAS: Readonly<Record<Categoria, string>> = {
  oleo: 'Óleo',
  flor: 'Flor',
  pomada: 'Pomada',
  creme: 'Creme',
  spray: 'Spray',
  goma: 'Goma',
};

export const CATEGORIAS_CHAVES = Object.keys(CATEGORIAS) as readonly Categoria[];

/** Data em que lemos o catálogo das associações que têm categorias informadas. */
export const CATALOGO_EM = '06/11/2026';

export const ORIGEM = 'weediario.com';

/** Mensagem pronta para a associação saber de onde a pessoa veio. */
export function mensagem(a: Associacao): string {
  return `Olá! Vim do ${ORIGEM} e gostaria de saber mais sobre a ${a.nome}.`;
}

export function linkWhatsapp(a: Associacao): string | null {
  return a.whatsapp ? `https://wa.me/${a.whatsapp}?text=${encodeURIComponent(mensagem(a))}` : null;
}

export function linkEmail(a: Associacao): string | null {
  return a.email
    ? `mailto:${a.email}?subject=${encodeURIComponent('Vim do ' + ORIGEM)}&body=${encodeURIComponent(mensagem(a))}`
    : null;
}

export function linkTelefone(a: Associacao): string | null {
  const digitos = a.telefone?.replace(/\D/g, '');
  return digitos ? `tel:${digitos}` : null;
}

export const LEVANTADO_EM = '06/11/2026';

export const ASSOCIACOES: readonly Associacao[] = [
  {
    slug: 'abrario',
    nome: 'AbraRio',
    cidade: 'Rio de Janeiro',
    uf: 'RJ',
    site: 'https://www.abrario.org/',
    outrosEstados: null,
    resumo:
      'Associação civil sem fins lucrativos que acolhe pacientes e dá orientação médica e jurídica para o acesso ao tratamento com cannabis medicinal.',
    logo: null,
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'apepi',
    nome: 'APEPI',
    cidade: 'Rio de Janeiro',
    uf: 'RJ',
    site: 'https://apepi.org/',
    outrosEstados: null,
    resumo:
      'Associação que acolhe e orienta pacientes, facilita o acesso à cannabis medicinal e apoia a pesquisa. Atua desde 2014.',
    logo: 'apepi.webp',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'ama-me',
    nome: 'Ama+me',
    cidade: 'Belo Horizonte',
    uf: 'MG',
    site: 'https://amame.org.br/',
    outrosEstados: true,
    resumo:
      'Associação brasileira de pacientes de cannabis medicinal, com acolhimento, apoio jurídico e uma lista de médicos prescritores de todo o Brasil.',
    logo: 'ama-me.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'abrace-esperanca',
    nome: 'Abrace Esperança',
    cidade: 'João Pessoa',
    uf: 'PB',
    site: 'https://abraceesperanca.org.br/',
    outrosEstados: null,
    resumo:
      'Associação de apoio à cannabis medicinal, de João Pessoa. Produz no Brasil extratos full spectrum, com certificado de análise.',
    logo: 'abrace-esperanca.jpg',
    whatsapp: '558398720072',
    email: 'contato@abraceesperanca.org.br',
    telefone: '0800 313 2367',
    categorias: ['oleo', 'flor', 'pomada', 'creme', 'spray', 'goma'],
  },
  {
    slug: 'ampara',
    nome: 'AMPARA',
    cidade: 'João Pessoa',
    uf: 'PB',
    site: 'https://amparacannabis.com.br',
    outrosEstados: null,
    resumo:
      'Associação de pacientes com suporte, educação e acolhimento para o acesso seguro ao tratamento com cannabis medicinal.',
    logo: 'ampara.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'amme-medicinal',
    nome: 'AMME Medicinal',
    cidade: 'Recife',
    uf: 'PE',
    site: 'https://ammemedicinal.org',
    outrosEstados: null,
    resumo:
      'Associação que acolhe famílias e cultiva a própria matéria-prima para os associados, com área do associado no site.',
    logo: 'amme-medicinal.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'cannab',
    nome: 'Cannab',
    cidade: null,
    uf: 'BA',
    site: 'https://cannab.com.br/',
    outrosEstados: null,
    resumo: 'Associação para a pesquisa e o desenvolvimento da cannabis medicinal no Brasil.',
    logo: 'cannab.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'abracam',
    nome: 'Abracam',
    cidade: 'Fortaleza',
    uf: 'CE',
    site: 'https://www.abracam.org',
    outrosEstados: null,
    resumo: 'Associação Brasileira de Cannabis Medicinal, com sede em Fortaleza.',
    logo: 'abracam.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'cultive',
    nome: 'Cultive',
    cidade: null,
    uf: 'SP',
    site: 'https://www.cultive.org.br',
    outrosEstados: null,
    resumo: 'Associação criada para suprir a falta de acesso legal à cannabis medicinal no país.',
    logo: 'cultive.svg',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'flor-da-vida',
    nome: 'Flor da Vida',
    cidade: 'Franca',
    uf: 'SP',
    site: 'https://www.flordavida.ong.br',
    outrosEstados: null,
    resumo:
      'Associação terapêutica sem fins lucrativos, com acolhimento e atendimento a pacientes de todo o Brasil.',
    logo: 'flor-da-vida.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'pro-vida-cannabis',
    nome: 'Pró-Vida Cannabis',
    cidade: null,
    uf: 'SP',
    site: 'https://www.providacannabis.com.br',
    outrosEstados: null,
    resumo:
      'Associação com 13 anos de atuação, cadastro gratuito e suporte médico e jurídico para pacientes.',
    logo: 'pro-vida-cannabis.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
  {
    slug: 'accura',
    nome: 'Accura',
    cidade: 'São Paulo',
    uf: 'SP',
    site: 'https://accura.org.br',
    outrosEstados: null,
    resumo:
      'Associação brasileira de apoio e pesquisa em cannabis medicinal, que também funciona como escola e comunidade.',
    logo: 'accura.png',
    whatsapp: null,
    email: null,
    telefone: null,
    categorias: [],
  },
];

export const UFS: readonly string[] = [...new Set(ASSOCIACOES.map((a) => a.uf))].sort((x, y) =>
  x.localeCompare(y, 'pt-BR'),
);

/** No estado primeiro, depois as que atendem outros estados, depois as demais; dentro de cada grupo, a ordem do cadastro. */
export function listar(uf: string): readonly Associacao[] {
  const peso = (a: Associacao) => (uf && a.uf === uf ? 0 : a.outrosEstados === true ? 1 : 2);
  return ASSOCIACOES.map((a, i) => ({ a, i }))
    .sort((x, y) => peso(x.a) - peso(y.a) || x.i - y.i)
    .map((x) => x.a);
}

/** Com um estado escolhido, só as do estado e as que atendem outros estados. Sem estado, todas. */
export function filtrar(uf: string, categoria: Categoria | '' = ''): readonly Associacao[] {
  return listar(uf).filter(
    (a) =>
      (!uf || a.uf === uf || a.outrosEstados === true) &&
      (!categoria || a.categorias.includes(categoria)),
  );
}
