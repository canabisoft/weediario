// GERADO a partir do guia de cultivo da Central da Cannabis e adaptado ao weediário (sem analytics, sem embed,
// sem fonte externa). Editar à mão é o esperado daqui para frente: este arquivo agora é a fonte do conteúdo.
//
// Texto com marcação mínima: **negrito**, *itálico* e [texto](link). Link que começa com "/" é interno.
// Nada de HTML: o componente monta tudo com elementos próprios, então a CSP (script-src 'self') nunca é tocada.

/**
 * Resumo mínimo das etapas, para o SEO (JSON-LD) sem carregar o conteúdo todo no bundle principal. Um teste garante que
 * os nomes batem com guia-cultivo.data.ts.
 */
export const ETAPAS_RESUMO: readonly { nome: string; descricao: string }[] = [
  {
    nome: 'Regularização',
    descricao: 'antes de tudo',
  },
  {
    nome: 'Planejar',
    descricao: 'espaço e data',
  },
  {
    nome: 'Montar',
    descricao: 'tenda, luz, vasos, terra',
  },
  {
    nome: 'Germinar',
    descricao: 'até 14 dias',
  },
  {
    nome: 'Plântula',
    descricao: '2 a 3 sem · 16 a 18 h de luz',
  },
  {
    nome: 'Vegetativo',
    descricao: 'até 8 sem · luz 18/6',
  },
  {
    nome: 'Floração',
    descricao: '7 a 12 sem · luz 12/12',
  },
  {
    nome: 'Colheita',
    descricao: 'cor dos tricomas',
  },
  {
    nome: 'Secar e curar',
    descricao: 'cura de 2 a 4 sem',
  },
];
