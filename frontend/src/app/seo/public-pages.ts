/** Endereço canônico do site: base dos links canonical, Open Graph, JSON-LD e sitemap. */
export const SITE_URL = 'https://weediario.com';
export const SITE_NAME = 'weediário';

export interface PublicPage {
  /** Caminho da rota, começando por "/". */
  readonly path: string;
  readonly title: string;
  readonly description: string;
  /** Imagem de compartilhamento 1200x630 em public/og/. */
  readonly ogImage: string;
}

/**
 * As únicas páginas que robôs sem conta enxergam. Cada uma é pré-renderizada no build (app.routes.server.ts), ganha
 * head próprio (SeoService) e entra no sitemap. Nada aqui vem de usuário: diários, fotos, perfis e avaliações ficam
 * fora do HTML público e do sitemap.
 */
export const PUBLIC_PAGES: readonly PublicPage[] = [
  {
    path: '/',
    title: 'weediário — diário de cultivo de cannabis para quem planta em casa',
    description:
      'Anote cada semana do cultivo com fotos, pH, rega e VPD. Catálogo aberto de genéticas e comunidade fechada, sem pedir nome, CPF nem telefone.',
    ogImage: '/og/home.jpg',
  },
  {
    path: '/manifesto',
    title: 'Manifesto · weediário — privacidade no cultivo de cannabis',
    description:
      'Por que o weediário não pede nome, CPF, e-mail nem guarda sua localização. O que a gente coleta, o que nunca coleta e como você apaga tudo.',
    ogImage: '/og/manifesto.jpg',
  },
  {
    path: '/strains',
    title: 'Catálogo de strains de cannabis — weediário',
    description:
      'Genéticas, bancos de sementes e produtos com as médias de avaliação da comunidade. Busque por nome, breeder ou tipo.',
    ogImage: '/og/strains.jpg',
  },
  {
    path: '/ferramentas',
    title: 'Calculadora de VPD e conversor EC/PPM — weediário',
    description:
      'Calculadora de VPD e conversor EC/PPM que funcionam offline, dentro da tenda. Sem cadastro.',
    ogImage: '/og/ferramentas.jpg',
  },
  {
    path: '/guia-de-cultivo',
    title: 'Como cultivar cannabis em casa gastando pouco: guia em 9 etapas — weediário',
    description:
      'Do planejamento à cura, em 9 etapas e com checklist: espaço, luz, germinação, rega, floração, colheita e secagem, com o que reaproveitar em vez de comprar. Sem cadastro.',
    ogImage: '/og/guia-de-cultivo.jpg',
  },
  {
    path: '/associacoes',
    title: 'Associações de cannabis medicinal por estado — weediário',
    description:
      'Encontre uma associação de pacientes de cannabis medicinal no seu estado e fale direto com ela. Lista gratuita e sem cadastro.',
    ogImage: '/og/home.jpg',
  },
  {
    path: '/dados-abertos',
    title: 'Banco aberto de strains em CSV e JSON — weediário',
    description:
      'Os números do catálogo do weediário para baixar em CSV ou JSON, sem dado pessoal de ninguém.',
    ogImage: '/og/dados-abertos.jpg',
  },
  {
    path: '/termos',
    title: 'Termos de Uso · weediário',
    description:
      'As regras do weediário: sem comércio, sem exposição de dados e o que acontece com quem quebra as regras.',
    ogImage: '/og/termos.jpg',
  },
  {
    path: '/privacidade',
    title: 'Política de Privacidade · weediário',
    description:
      'Quem trata seus dados no weediário, com que base legal, com quem compartilhamos e como pedir acesso, correção ou exclusão.',
    ogImage: '/og/privacidade.jpg',
  },
  {
    path: '/breeders/programa',
    title: 'Programa de breeders · weediário',
    description:
      'Para bancos e breeders independentes cadastrarem genéticas validadas pela comunidade.',
    ogImage: '/og/breeders.jpg',
  },
];

export function publicPage(path: string): PublicPage | undefined {
  return PUBLIC_PAGES.find((page) => page.path === path);
}
