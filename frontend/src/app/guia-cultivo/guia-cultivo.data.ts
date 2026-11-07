// GERADO a partir do guia de cultivo da Central da Cannabis e adaptado ao weediário (sem analytics, sem embed,
// sem fonte externa). Editar à mão é o esperado daqui para frente: este arquivo agora é a fonte do conteúdo.
//
// Texto com marcação mínima: **negrito**, *itálico* e [texto](link). Link que começa com "/" é interno.
// Nada de HTML: o componente monta tudo com elementos próprios, então a CSP (script-src 'self') nunca é tocada.

export type Bloco =
  | { t: 'p'; x: string }
  | { t: 'h'; x: string }
  | { t: 'ul' | 'ol'; itens: string[] }
  | { t: 'tabela'; cab: string[]; linhas: string[][] }
  | { t: 'nota'; tipo: 'gastando' | 'erro' | 'aviso' | 'info'; x: string }
  | { t: 'caixa'; titulo: string; blocos: Bloco[] }
  | { t: 'receita'; titulo: string; itens: string[] }
  | { t: 'link'; x: string; href: string }
  | { t: 'lojas'; itens: string[] };

export interface Foto {
  /** Arquivo em public/guia-cultivo/ (servido do próprio site: a CSP só aceita imagem de 'self'). */
  arq: string;
  alt: string;
  autor: string;
  licenca: string;
  /** Página da foto no Wikimedia Commons, com a licença completa. */
  origem: string;
  /** Nome do arquivo no Commons. */
  arquivo: string;
}

export interface Video {
  /** Vira link para o YouTube. Não há player embutido nem miniatura: a CSP não deixa e o app não chama terceiros. */
  id: string;
  canal: string;
  tema: string;
  duracao: string;
  titulo: string;
}

export interface Etapa {
  id: number;
  curto: string;
  sub: string;
  chips: string[];
  blocos: Bloco[];
  fotos: string[];
  videos: Video[];
  checklist: string[];
}

export interface Loja {
  loja: string;
  nome: string;
  preco?: string;
  nota?: string;
  url: string;
}

export interface ItemCompra {
  chave: string;
  item: string;
  barata: string;
  quando: string;
}

export const FOTOS: Record<string, Foto> = {
  montar: {
    arq: '/guia-cultivo/montar.jpg',
    alt: 'Cultivo interno com tenda e luz artificial.',
    autor: 'Plantlady223',
    licenca: 'CC BY-SA 4.0',
    origem: 'https://commons.wikimedia.org/wiki/File:Indoor_cannabis_plants.jpg',
    arquivo: 'Indoor cannabis plants.jpg',
  },
  germinar: {
    arq: '/guia-cultivo/germinar.jpg',
    alt: 'Muda recém-saída da semente, com o primeiro par de folhas (cotilédones).',
    autor: 'Grower',
    licenca: 'domínio público',
    origem: 'https://commons.wikimedia.org/wiki/File:Cannabis_Baby.jpg',
    arquivo: 'Cannabis Baby.jpg',
  },
  plantula: {
    arq: '/guia-cultivo/plantula.jpg',
    alt: 'Plântula: poucas folhas verdadeiras e caule ainda fino.',
    autor: 'My 420 Tours',
    licenca: 'CC BY-SA 4.0',
    origem: 'https://commons.wikimedia.org/wiki/File:Seedling_cannabis_plant.jpg',
    arquivo: 'Seedling cannabis plant.jpg',
  },
  vegetativo: {
    arq: '/guia-cultivo/vegetativo.jpg',
    alt: 'Planta jovem na fase vegetativa, com folhas largas e crescimento rápido.',
    autor: 'Plantlady223',
    licenca: 'CC BY-SA 4.0',
    origem:
      'https://commons.wikimedia.org/wiki/File:Young_cannabis_plant_in_the_vegetative_stage_01.jpg',
    arquivo: 'Young cannabis plant in the vegetative stage 01.jpg',
  },
  floracao: {
    arq: '/guia-cultivo/floracao.jpg',
    alt: 'Flor em desenvolvimento, vista de perto.',
    autor: 'HighInBC (atribuir a yogi Bushby)',
    licenca: 'CC BY 2.5',
    origem: 'https://commons.wikimedia.org/wiki/File:Macro_cannabis_bud.jpg',
    arquivo: 'Macro cannabis bud.jpg',
  },
  colheita: {
    arq: '/guia-cultivo/colheita.jpg',
    alt: 'Tricomas (as gotinhas de resina) vistos de perto: a cor deles indica a hora da colheita.',
    autor: 'Indirectantagonist',
    licenca: 'CC BY-SA 3.0',
    origem: 'https://commons.wikimedia.org/wiki/File:Trichomes_on_a_Cannabis_Sativa_Flower.jpg',
    arquivo: 'Trichomes on a Cannabis Sativa Flower.jpg',
  },
  secar: {
    arq: '/guia-cultivo/secar.jpg',
    alt: 'Flores penduradas para secar.',
    autor: 'Cannabis Training University',
    licenca: 'CC BY-SA 3.0',
    origem: 'https://commons.wikimedia.org/wiki/File:Drying_Cannabis_Buds.jpg',
    arquivo: 'Drying Cannabis Buds.jpg',
  },
  curar: {
    arq: '/guia-cultivo/curar.jpg',
    alt: 'Flores curando em pote de vidro.',
    autor: 'Cannabis Culture',
    licenca: 'CC BY 2.0',
    origem: 'https://commons.wikimedia.org/wiki/File:JarOfMarijuana.jpg',
    arquivo: 'JarOfMarijuana.jpg',
  },
};

export const ETAPAS: readonly Etapa[] = [
  {
    id: 0,
    curto: 'Regularização',
    sub: 'antes de tudo',
    chips: ['Antes de comprar qualquer coisa'],
    blocos: [
      {
        t: 'p',
        x: 'Cultivar cannabis em casa não é liberado no Brasil. Mas a lei mudou o que acontece com quem cultiva pouco, e é importante entender exatamente como.',
      },
      {
        t: 'caixa',
        titulo: 'O que o STF decidiu sobre 6 plantas',
        blocos: [
          {
            t: 'p',
            x: 'Em 26 de junho de 2024, o STF (RE 635659, Tema 506) decidiu que **portar maconha para uso pessoal não é crime**: é uma infração administrativa. Para separar usuário de traficante, passou a valer um critério: **é presumido usuário quem tiver até 40 gramas ou até 6 plantas fêmeas**. Em fevereiro de 2025, o plenário confirmou a decisão por unanimidade.',
          },
          {
            t: 'p',
            x: 'Dentro desse limite não há sanção penal. Mas leia o que isso *não* significa:',
          },
          {
            t: 'ul',
            itens: [
              '**Não é autorização para plantar.** É um critério para decidir se a pessoa é usuária, não uma licença de cultivo.',
              'A presunção é relativa: com outros indícios de tráfico, a polícia pode apreender as plantas e levar a pessoa à delegacia, mesmo abaixo do limite.',
              'A regra vale até o Congresso aprovar uma lei sobre o assunto.',
              'As notícias consultadas listam as condutas (comprar, guardar, transportar), e o limite de 6 plantas fêmeas aparece como parâmetro. O texto completo do acórdão, no site do STF, é o que vale.',
            ],
          },
          {
            t: 'link',
            x: 'Ler a notícia do STF sobre a decisão',
            href: 'https://noticias.stf.jus.br/postsnoticias/stf-define-40-gramas-de-maconha-como-criterio-para-diferenciar-usuario-de-traficante/',
          },
        ],
      },
      {
        t: 'h',
        x: 'E para uso medicinal?',
      },
      {
        t: 'p',
        x: 'Quem tem prescrição médica pode buscar na Justiça uma autorização para cultivar (o chamado salvo-conduto), e há decisões nesse sentido. Algumas associações de pacientes ajudam nesse processo; pergunte a elas, e a um advogado, como funciona e quanto custa. Vale também conversar com o médico que acompanha o caso.',
      },
      {
        t: 'nota',
        tipo: 'aviso',
        x: 'Este texto é informativo e não é aconselhamento jurídico. As regras mudam e cada caso é um caso.',
      },
      {
        t: 'h',
        x: 'Por que pensar em até 6 plantas fêmeas',
      },
      {
        t: 'p',
        x: 'Só as fêmeas produzem as flores que se usam. Por isso, e para ficar dentro do critério acima, o planejamento começa pensando em poucas plantas, que também são mais fáceis de cuidar com atenção.',
      },
    ],
    fotos: [],
    videos: [
      {
        id: '3BpKOlV5oPc',
        canal: 'Leds Indoor',
        tema: 'A lei',
        duracao: '14:33',
        titulo: 'É CRIME CULTIVAR NO BRASIL? | SOS CULTIVO #4',
      },
      {
        id: 'g7yP5k5ZMYY',
        canal: 'Cultlight',
        tema: 'As 6 plantas',
        duracao: '1:10:25',
        titulo:
          '6 Pés de Maconha: A Descriminalização Mudou Alguma Coisa de Fato? (Advogado Clayton)',
      },
    ],
    checklist: [
      'Li a decisão do STF e entendi que ela não autoriza o cultivo',
      'Conversei com o médico (se o uso for medicinal)',
      'Pesquisei salvo-conduto com um advogado ou uma associação',
      'Decidi plantar no máximo 6 plantas fêmeas',
    ],
  },
  {
    id: 1,
    curto: 'Planejar',
    sub: 'espaço e data',
    chips: ['Antes de comprar', 'Espaço + calendário', 'Onde comprar sementes'],
    blocos: [
      {
        t: 'p',
        x: 'Antes de gastar, decida três coisas: **onde** vai plantar, **o que** vai plantar e **quando** começa.',
      },
      {
        t: 'h',
        x: '1. Onde: dentro, fora ou os dois',
      },
      {
        t: 'tabela',
        cab: ['', 'Indoor (dentro)', 'Outdoor (fora)'],
        linhas: [
          [
            'Vantagens',
            'Você decide a luz, a temperatura e a rega; dá para fazer mais de um ciclo por ano; a planta fica fora das vistas.',
            'Custo muito baixo; as plantas ficam grandes com o sol; quase não há gasto de energia.',
          ],
          [
            'Desvantagens',
            'Gasto inicial com equipamento e conta de luz mais alta; sem atenção, uma praga se espalha rápido num espaço fechado.',
            'Depende de chuva, calor e estação; pragas, animais e vizinhos curiosos; em geral uma colheita por ano.',
          ],
        ],
      },
      {
        t: 'p',
        x: 'Há também o modelo **misto**: sol durante o dia e lâmpada para completar. Ele junta os pontos fortes e os fracos dos dois.',
      },
      {
        t: 'h',
        x: '2. O que: o tipo de semente',
      },
      {
        t: 'ul',
        itens: [
          '**Feminizada:** nasce quase sempre fêmea, que é a planta que dá as flores. Poupa o trabalho de descobrir e descartar machos.',
          '**Regular:** costuma custar menos, mas pode nascer macho, fêmea ou hermafrodita, e isso só aparece nas pré-flores.',
          '**Automática:** floresce pela idade da planta, não pelo horário da luz. O ciclo todo (crescimento mais floração) leva até uns 80 dias.',
        ],
      },
      {
        t: 'p',
        x: 'Para começar, escolha **uma variedade só** e **poucas plantas**: assim dá para olhar cada uma todo dia.',
      },
      {
        t: 'h',
        x: 'Como comprar sementes, passo a passo',
      },
      {
        t: 'ol',
        itens: [
          '**Decida o tipo.** Na primeira vez, feminizada ou automática costumam dar menos dor de cabeça do que regular.',
          '**Escolha a variedade.** Leia a página do produto: se é automática ou de fotoperíodo, quanto tempo leva, que altura alcança e se serve para cultivo interno. Variedades fáceis de cuidar são melhores para quem está começando.',
          '**Escolha a loja.** Logo abaixo estão duas lojas brasileiras. Compare o mesmo tipo de semente nas duas.',
          '**Confira antes de pagar.** O endereço começa com https? Há política de envio, de troca e um canal de atendimento? O produto vem de um banco de sementes conhecido, em embalagem original? Desconfie de preço muito abaixo do normal e de quem pede Pix para pessoa física.',
          '**Compre só o que vai plantar.** Poucos pacotes bastam: quem pretende ficar em até 6 plantas fêmeas não precisa de mais.',
          '**Guarde com cuidado.** Até a hora de germinar, as sementes ficam num lugar fresco, escuro e seco.',
        ],
      },
      {
        t: 'lojas',
        itens: ['sementes'],
      },
      {
        t: 'nota',
        tipo: 'info',
        x: 'Pacotes de bancos conhecidos costumam custar de uns R$ 130 a mais de R$ 600, conforme o banco, a genética e o número de sementes (consulta nas páginas iniciais das lojas em 06/11/2026). O frete e a forma de pagamento mudam de loja para loja.',
      },
      {
        t: 'caixa',
        titulo: 'Antes de comprar: a situação legal é indefinida',
        blocos: [
          {
            t: 'p',
            x: 'Não há uma lei brasileira que libere nem que proíba com clareza a compra de sementes de cannabis. As lojas vendem como "sementes de coleção", e a Justiça e a polícia tratam o assunto de formas diferentes: já houve operações contra vendedores pela internet. Plantar continua sujeito ao que explicamos na etapa 0. Compre por sua conta e risco e, se tiver dúvida, converse com um advogado.',
          },
          {
            t: 'link',
            x: 'Ler uma análise sobre o assunto',
            href: 'https://poder360.com.br/opiniao/sementes-de-maconha-no-brasil-legais-ou-ilegais/',
          },
        ],
      },
      {
        t: 'h',
        x: 'Dá para começar com semente de prensado também',
      },
      {
        t: 'p',
        x: 'Sementes que vêm no prensado podem germinar, e é um jeito de começar sem gastar com semente. Só que isso tem um custo escondido:',
      },
      {
        t: 'ul',
        itens: [
          '**Metade pode ser macho.** Sem ser feminizada, cerca de metade das plantas costuma nascer macho e precisa ser descartada. Se o limite é de 6 plantas fêmeas, germine mais sementes do que isso.',
          '**Genética desconhecida.** Ninguém sabe o ciclo, o tamanho nem o que esperar. Para uso medicinal isso atrapalha, porque o efeito pode variar de uma planta para outra.',
          '**Nem toda semente vinga.** Prefira as escuras, duras e com riscas. As claras, verdes ou que esmagam fácil quase nunca germinam.',
          '**Risco de hermafrodita.** Algumas plantas desenvolvem flores dos dois sexos e podem polinizar as outras.',
        ],
      },
      {
        t: 'p',
        x: 'Germinar e cuidar é igual ao das etapas 3 e 4. Se o objetivo é tratamento, vale mais a pena uma variedade conhecida.',
      },
      {
        t: 'h',
        x: '3. Quando: faça a conta do ciclo',
      },
      {
        t: 'p',
        x: 'Some o tempo de cada fase e deixe uma margem, porque imprevistos acontecem. Exemplo para semente feminizada em ambiente interno:',
      },
      {
        t: 'ol',
        itens: [
          'Germinação: até 14 dias (2 semanas).',
          'Vegetação (plântula incluída): até 8 semanas.',
          'Floração: de 7 a 12 semanas, conforme a genética.',
          'Total até a colheita: no máximo uns 22 semanas, cerca de 5 meses.',
          'Depois vem a secagem e a cura (2 a 4 semanas).',
        ],
      },
      {
        t: 'p',
        x: 'Fora de casa, as fases seguem as estações; descubra quais meses funcionam na sua região. Evite começar um ciclo logo antes de uma viagem ou de qualquer período em que você não possa regar e vistoriar as plantas.',
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'comece com 1 a 3 plantas e um caderno. Anotar tudo (datas, rega, o que mudou) é de graça e é o que mais evita erro no segundo ciclo.',
      },
    ],
    fotos: [],
    videos: [
      {
        id: 'R7ZBql-HKSE',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Para começar',
        duracao: '8:56',
        titulo:
          'TUDO que você PRECISA saber antes de PLANTAR ERVA | Primeiros Passos, Dicas e Motivos pra Começar',
      },
      {
        id: '4uLZIMc2Ga8',
        canal: 'Leds Indoor',
        tema: 'Dentro ou fora',
        duracao: '8:32',
        titulo: 'Cultivo Indoor x Outdoor: Qual Compensa Mais em 2026?',
      },
      {
        id: 'tdT1LFnb-zQ',
        canal: 'Cultlight',
        tema: 'Visão geral',
        duracao: '17:07',
        titulo: 'Guia de Cultivo 2026 | Episódio 1 - O que você precisa saber',
      },
    ],
    checklist: [
      'Escolhi onde vou plantar',
      'Escolhi o tipo de semente e quantas plantas',
      'Escolhi onde conseguir as sementes (loja ou semente de prensado)',
      'Calculei a data de início e a data provável da colheita, com margem',
      'Comecei um diário de cultivo (caderno ou celular)',
    ],
  },
  {
    id: 2,
    curto: 'Montar',
    sub: 'tenda, luz, vasos, terra',
    chips: ['Uma tarde de trabalho', 'Pode ser reaproveitado'],
    blocos: [
      {
        t: 'p',
        x: 'Toda estufa caseira precisa de seis coisas: **espaço fechado**, **luz**, **vasos**, **terra**, **ar circulando** e **medidores**. Dá para comprar tudo pronto ou montar com o que tem em casa.',
      },
      {
        t: 'h',
        x: 'Espaço (a "tenda")',
      },
      {
        t: 'p',
        x: 'É o compartimento fechado onde a planta vive. Pode ser uma tenda pronta ou uma estrutura sua, forrada com lona ou mylar (material que reflete a luz). Um armário ou uma sapateira adaptados também servem. Defina antes quantas plantas serão, porque é isso que decide o tamanho.',
      },
      {
        t: 'h',
        x: 'Luz',
      },
      {
        t: 'p',
        x: 'Hoje a maioria usa painéis de LED. As lâmpadas HPS ainda existem, mas esquentam bem mais o ambiente. O horário é o que importa: **18 h de luz e 6 h de escuro** enquanto a planta cresce, e **12 h de luz e 12 h de escuro** para florescer. Um timer de tomada cuida disso sozinho.',
      },
      {
        t: 'h',
        x: 'Vasos',
      },
      {
        t: 'p',
        x: 'O fundo precisa de furos para a água escorrer. Baldes e garrafas grandes cortadas funcionam. Vaso muito escuro esquenta no sol; vaso transparente deixa a luz bater na raiz, o que a prejudica.',
      },
      {
        t: 'h',
        x: 'Terra (substrato)',
      },
      {
        t: 'p',
        x: 'A terra precisa ser solta e arejada, e deixar a água passar. Dá para comprar uma mistura pronta ou fazer a sua. Um ponto de partida comum é misturar duas partes de fibra de coco, uma de perlita (que arejar) e uma de húmus de minhoca (que nutre), ajustando depois conforme a drenagem. Não é a única receita possível.',
      },
      {
        t: 'p',
        x: 'Antes de plantar, faça um teste: regue o vaso só com água durante alguns dias. Observe se a água escorre pelo fundo e quanto tempo a terra leva para secar. Se alagar, acrescente perlita.',
      },
      {
        t: 'h',
        x: 'Ar',
      },
      {
        t: 'p',
        x: 'O ar parado favorece mofo e pragas. Um ventilador pequeno dentro da tenda movimenta as folhas, e um exaustor (ou um cooler de computador adaptado) troca o ar. Se o cheiro incomodar, filtro de carvão ativado ajuda.',
      },
      {
        t: 'h',
        x: 'Medidores',
      },
      {
        t: 'p',
        x: 'O termo-higrômetro mostra a temperatura e a umidade do ar, e assim você percebe quando o ambiente sai do ponto. Medidores de pH e de EC (que indica a quantidade de sais na água) ajudam a ajustar a rega e o adubo. Regador, tesoura e borrifador completam o kit.',
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'reaproveite armário ou sapateira como tenda, vasos de balde furado, um cooler ou ventilador doméstico e um timer comum de tomada. Compre com mais cuidado a *luz* e o *termo-higrômetro*, porque são o que mais influencia o resultado.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'deixar vazar luz no período de escuro. Na floração, mais de 12 h de luz faz a planta voltar a crescer (revegetar).',
      },
      {
        t: 'lojas',
        itens: ['armario', 'luz', 'timer', 'ventilacao', 'termo', 'vasos', 'substrato', 'ph'],
      },
    ],
    fotos: ['montar'],
    videos: [
      {
        id: '4fxRYMsVdIE',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Gastando pouco',
        duracao: '13:35',
        titulo: 'Como cultivar em casa GASTANDO POUCO + Growbox de Floração - EP. De Cultivo',
      },
      {
        id: '7vnnycxXOmk',
        canal: 'Leds Indoor',
        tema: 'Kit barato',
        duracao: '9:53',
        titulo: 'Grow Indoor Barato Existe? Como Montar um Kit Low Budget do Zero (Passo a Passo)',
      },
      {
        id: 'gfzAhbKxUVQ',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Armário',
        duracao: '14:49',
        titulo:
          'Como Fazer uma GROWBIARRA de SUCESSO no Seu Guarda-Roupa com um Orçamento LIMITADO',
      },
      {
        id: 'Q_x6784kpdg',
        canal: 'Cultlight',
        tema: 'Luz',
        duracao: '19:29',
        titulo:
          'Iluminação para Cultivo Indoor: Tudo o Que Você Precisa Saber | Guia de Cultivo 2026 (Ep. 4)',
      },
      {
        id: '1eD1sncWAwA',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Terra',
        duracao: '24:43',
        titulo: 'SUBSTRATO ORGÂNICO FÁCIL - Receita de super solo vivo',
      },
    ],
    checklist: [
      'Escolhi e montei o espaço',
      'Instalei a luz e o timer (18/6 e depois 12/12)',
      'Tenho vasos com furo de drenagem',
      'Preparei a terra e testei a drenagem só com água',
      'Montei a ventilação e o termo-higrômetro',
    ],
  },
  {
    id: 3,
    curto: 'Germinar',
    sub: 'até 14 dias',
    chips: ['Até 14 dias', 'Custo quase zero'],
    blocos: [
      {
        t: 'p',
        x: 'Germinar é fazer a semente abrir e soltar a primeira raiz. Os jeitos mais comuns em casa são três:',
      },
      {
        t: 'ul',
        itens: [
          "**Copo d'água:** a semente fica de molho até rachar.",
          '**Papel úmido:** a semente fica entre dois pedaços de papel toalha ou algodão.',
          '**Direto na terra:** ou em pastilhas e copinhos próprios, que vendem prontos.',
        ],
      },
      {
        t: 'h',
        x: 'Passo a passo no papel toalha',
      },
      {
        t: 'ol',
        itens: [
          'Umedeça (sem encharcar) dois pedaços de papel toalha ou algodão.',
          'Coloque a semente entre eles e deixe num prato ou pote tampado, num lugar morno e escuro.',
          'Olhe uma vez por dia e mantenha úmido, nunca ensopado.',
          'Quando a raiz tiver alguns milímetros, passe a semente para a terra com a raiz para baixo.',
          'Regue com cuidado e mantenha a terra úmida, mas não alagada.',
        ],
      },
      {
        t: 'p',
        x: 'Em poucos dias a casca abre e a raiz aparece; logo depois sobem as primeiras folhas. Se passar de duas semanas sem sinal nenhum, a semente provavelmente não vai vingar.',
      },
      {
        t: 'p',
        x: 'Dica: prefira sementes inteiras e firmes, guardadas longe de umidade, calor e luz.',
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'papel toalha, um prato e um pote tampado resolvem. Não precisa de kit.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'encharcar. Semente muito molhada fica sem ar e apodrece.',
      },
    ],
    fotos: ['germinar'],
    videos: [
      {
        id: 'cVnRvZ8uMCo',
        canal: 'Plantamemo',
        tema: 'Rápido',
        duracao: '3:13',
        titulo: 'COMO GERMINAR SEMENTE DE MACONHA EM 3 MINUTOS (Muito Fácil)',
      },
      {
        id: 'd3MRjITzoN4',
        canal: 'Leds Indoor',
        tema: 'Passo a passo',
        duracao: '14:41',
        titulo:
          'Germinar Semente de Cannabis Rápido e Fácil! Passo a Passo para Brotar Semente de Maconha',
      },
      {
        id: 'YFa-VytKBY8',
        canal: 'Cultlight',
        tema: 'Germinar e mudas',
        duracao: '27:49',
        titulo: 'Como Germinar Sementes e Cuidar das Mudas | Guia de Cultivo #8',
      },
    ],
    checklist: [
      'Germinei as sementes (água, papel ou célula)',
      'A raiz apareceu e passei a semente para a terra',
      'Anotei a data no diário',
    ],
  },
  {
    id: 4,
    curto: 'Plântula',
    sub: '2 a 3 sem · 16 a 18 h de luz',
    chips: ['2 a 3 semanas', '16 a 18 h de luz'],
    blocos: [
      {
        t: 'p',
        x: 'É a fase mais delicada. Deixe a luz ligada de **16 a 18 horas por dia**. A planta nova pede luz mais suave, então até uma lâmpada fluorescente dá conta. O ar deve circular de leve, sem vento forte.',
      },
      {
        t: 'p',
        x: 'Nos primeiros dias a plântula vive da reserva da própria semente, então **não adube**. Se quiser estimular a raiz, existem enraizadores caseiros; os mais citados usam água de arroz ou lentilha.',
      },
      {
        t: 'h',
        x: 'Rega',
      },
      {
        t: 'p',
        x: 'Regue pouco e perto do caule: nessa fase, um pequeno volume já basta. Espere a camada de cima secar e o vaso ficar mais leve antes de regar de novo. Prefira o começo da manhã ou o fim da tarde.',
      },
      {
        t: 'h',
        x: 'Os três pilares: água, luz e nutrientes',
      },
      {
        t: 'p',
        x: 'O segredo é o equilíbrio entre os três. Estes são os desvios mais comuns:',
      },
      {
        t: 'tabela',
        cab: ['Problema', 'Como aparece', 'O que fazer'],
        linhas: [
          [
            'Água demais',
            'Folhas pesadas e murchas mesmo com a terra molhada, crescimento parado, mosquitinhas e limo verde na superfície.',
            'Espaçar as regas e conferir se o vaso drena.',
          ],
          [
            'Pouca luz',
            'Planta alta e fina, com grandes vãos entre as folhas, e verde bem escuro.',
            'Aproximar a luz e conferir o horário.',
          ],
          [
            'Adubo demais',
            'Pontas secas e queimadas, folhas amarelando, manchas escuras.',
            'Agir logo: passar bastante água pela terra (lavagem) ou trocar parte dela.',
          ],
        ],
      },
      {
        t: 'h',
        x: 'Primeiro transplante',
      },
      {
        t: 'p',
        x: 'Passe a muda para um vaso só um pouco maior, e não para um gigante: terra demais fica encharcada e a raiz pequena não consegue aproveitar. Vá aumentando o vaso em etapas. Mexa na raiz com cuidado e evite deixá-la exposta à luz.',
      },
      {
        t: 'p',
        x: 'Quando as raízes já ocuparam o vaso, em geral por volta da terceira semana, chega a hora do segundo transplante.',
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'o enraizador caseiro quase não custa nada, e regar menos é de graça e evita o erro mais comum.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'regar todo dia "por carinho". A raiz precisa de ar tanto quanto de água.',
      },
    ],
    fotos: ['plantula'],
    videos: [
      {
        id: 'q3-dx9cUo9E',
        canal: 'Cultlight',
        tema: 'Rega',
        duracao: '0:39',
        titulo: 'NÃO REGUE DEMAIS: mudas de cannabis',
      },
      {
        id: '0E19XLH5TJs',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Rega',
        duracao: '18:46',
        titulo: 'Descubra O Segredo Para Regar Com A Frequência CERTA!',
      },
      {
        id: 'VMj0OULrrBE',
        canal: 'Leds Indoor',
        tema: 'Erros comuns',
        duracao: '13:25',
        titulo: 'Erros de iniciante no cultivo indoor: os 5 que mais custam',
      },
    ],
    checklist: [
      'Luz ligada de 16 a 18 h por dia',
      'Rego só quando a terra secou, sem encharcar',
      'Fiz o primeiro transplante sem usar vaso gigante',
      'Confiro a planta todo dia (cor, postura, terra)',
    ],
  },
  {
    id: 5,
    curto: 'Vegetativo',
    sub: 'até 8 sem · luz 18/6',
    chips: ['Até 8 semanas', '18 h de luz e 6 h de escuro'],
    blocos: [
      {
        t: 'p',
        x: 'Agora a planta ganha folhas, galhos e raízes. Partindo de semente, em ambiente interno, conta-se até 8 semanas (pode ser menos). Partindo de clone, a contagem começa depois que ele enraíza. Fora de casa, o sol decide.',
      },
      {
        t: 'h',
        x: 'Nutrição: o foco é o nitrogênio',
      },
      {
        t: 'p',
        x: 'É o nutriente que mais pesa no crescimento. Fontes orgânicas baratas: húmus de minhoca, farinha de peixe, torta de mamona e casca de ovo seca e moída (esta traz cálcio). Misture na terra ou use em regas, sempre em pouca quantidade.',
      },
      {
        t: 'h',
        x: 'Poda e amarração',
      },
      {
        t: 'p',
        x: 'Curvar e prender os galhos aos poucos, para a planta crescer mais larga do que alta, chama-se LST (treinamento de baixo estresse). Custa quase nada: barbante, arame macio e paciência.',
      },
      {
        t: 'h',
        x: 'Pragas: previna primeiro',
      },
      {
        t: 'p',
        x: 'Pragas dão mais trabalho do que qualquer outra coisa. Comece com o espaço bem limpo e, toda semana, vire as folhas e olhe a parte de baixo.',
      },
      {
        t: 'tabela',
        cab: ['Praga', 'Sinal', 'Prevenção'],
        linhas: [
          [
            'Mosca-branca',
            'Insetinhos brancos sob as folhas, que amarelam.',
            'Limpeza, óleo de neem e cartão adesivo amarelo.',
          ],
          [
            'Ácaro (aranha-vermelha)',
            'Pontinhos amarelos na folha e teia bem fina. Prefere ambiente quente e seco.',
            'Temperatura moderada (abaixo de uns 26 °C), ar circulando e umidade adequada.',
          ],
          [
            'Trips',
            'Riscos prateados e pontinhos escuros nas folhas.',
            'Cartão adesivo azul e óleo de neem.',
          ],
          [
            'Mosquinha da terra',
            'Mosquitinhas pretas saem do vaso quando você mexe nele.',
            'Deixar a terra secar entre as regas e usar cartão amarelo.',
          ],
        ],
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'casca de ovo, húmus e barbante resolvem adubo e amarração. Um frasco de óleo de neem e alguns cartões adesivos já cobrem boa parte da prevenção.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'esperar a praga "ficar feia" para agir. Quanto mais cedo, mais fácil.',
      },
    ],
    fotos: ['vegetativo'],
    videos: [
      {
        id: 'N8eMCAVwjMU',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Quanto tempo',
        duracao: '1:02:45',
        titulo: 'DESCUBRA: Qual é o TEMPO IDEAL de VEGETATIVO? Isso NINGUÉM FALA!',
      },
      {
        id: 'xHaxIHWaI4c',
        canal: 'Plantamemo',
        tema: 'Amarração (LST)',
        duracao: '19:27',
        titulo: 'LST: Low Stress Training (Timelapse)',
      },
      {
        id: 'kYO4MlroYhk',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Adubo',
        duracao: '20:21',
        titulo: 'Como USAR CORRETAMENTE o HÚMUS de MINHOCA?',
      },
      {
        id: 'VPqMjUC_KTo',
        canal: 'Leds Indoor',
        tema: 'pH e EC',
        duracao: '14:04',
        titulo: 'Medidor de pH e EC: como usar e calibrar no cultivo',
      },
    ],
    checklist: [
      'Luz em 18 h de luz e 6 h de escuro',
      'Fiz o segundo transplante',
      'Comecei a amarração (LST) dos galhos',
      'Olho o verso das folhas toda semana',
      'Anoto no diário o que muda na planta',
    ],
  },
  {
    id: 6,
    curto: 'Floração',
    sub: '7 a 12 sem · luz 12/12',
    chips: ['De 7 a 12 semanas', '12 h de luz e 12 h de escuro'],
    blocos: [
      {
        t: 'p',
        x: 'Quando a planta estiver forte e do tamanho que você quer, o horário muda para **12 h de luz e 12 h de escuro**. Esse é o sinal que faz a planta florescer. A fase dura de 7 a 12 semanas, dependendo da variedade.',
      },
      {
        t: 'p',
        x: 'Com semente regular, é nas **pré-flores** que se descobre o sexo. As automáticas florescem por conta própria, mesmo com mais de 12 h de luz.',
      },
      {
        t: 'h',
        x: 'Cuidado com a luz',
      },
      {
        t: 'p',
        x: 'Nas horas de escuro não pode entrar luz nenhuma, nem a de um aparelho com LED. Passar de 12 h de luz faz a planta interromper a floração e voltar a crescer.',
      },
      {
        t: 'h',
        x: 'Nutrição: foco em fósforo e potássio',
      },
      {
        t: 'ul',
        itens: [
          'Farinha de osso, uma fonte de fósforo.',
          'Casca de banana seca e triturada, que traz potássio.',
          'Melaço, em pouca quantidade, que alimenta a vida da terra.',
        ],
      },
      {
        t: 'h',
        x: 'Atenção na reta final',
      },
      {
        t: 'p',
        x: 'Lagartas atacam flores, principalmente em cultivo ao ar livre, e deixam o caminho aberto para mofo. O *Bacillus thuringiensis* (Bt) é um controle biológico conhecido contra elas.',
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'casca de banana seca, farinha de osso e melaço substituem muito adubo de loja. Um timer barato garante o 12/12 todos os dias.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'abrir a tenda com a luz acesa no horário do escuro. Uma luz forte, mesmo rápida, pode atrapalhar.',
      },
    ],
    fotos: ['floracao'],
    videos: [
      {
        id: 'qIPxUBypu6Q',
        canal: 'Cultlight',
        tema: 'Antes de florir',
        duracao: '47:25',
        titulo: 'Como Preparar Sua Planta Para Render Muito na Floração #09',
      },
      {
        id: 'zpSHFFwWmr0',
        canal: 'Cultlight',
        tema: 'Durante a floração',
        duracao: '31:01',
        titulo: 'Floração: Como Controlar Rega, Nutrição e Ambiente #10',
      },
    ],
    checklist: [
      'Troquei a luz para 12 h de luz e 12 h de escuro',
      'Confirmei que a tenda fica totalmente escura',
      'Mudei a nutrição para fósforo e potássio',
      'Confiro as flores toda semana (pragas e mofo)',
    ],
  },
  {
    id: 7,
    curto: 'Colheita',
    sub: 'cor dos tricomas',
    chips: ['No fim da floração'],
    blocos: [
      {
        t: 'p',
        x: 'Como saber se a planta está madura? Olhe os **tricomas**, os minúsculos pelinhos brilhantes cobertos de resina que ficam nas flores. É neles que se concentram os canabinoides.',
      },
      {
        t: 'p',
        x: 'Vistos de perto, os tricomas mudam de cor: começam transparentes, passam a leitosos e depois ficam cor de âmbar. O melhor momento depende da variedade e do objetivo. Para uso medicinal, converse com o médico sobre o ponto que faz mais sentido no seu caso.',
      },
      {
        t: 'p',
        x: 'O cheiro também muda ao longo da maturação, então vale anotar o que você sente a cada semana.',
      },
      {
        t: 'h',
        x: 'Como conferir',
      },
      {
        t: 'ol',
        itens: [
          'Use uma lupa (de joalheiro ou a câmera macro do celular).',
          'Olhe os tricomas das flores, não os das folhas.',
          'Anote a cor predominante e a data no diário.',
          'Colha quando o conjunto estiver no ponto que você escolheu.',
        ],
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'a câmera do celular com zoom ou uma lupa simples já mostram a cor dos tricomas. Não precisa de equipamento caro.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'colher pela data do calendário em vez de pela aparência da planta. A data é só uma estimativa.',
      },
      {
        t: 'lojas',
        itens: ['lupa'],
      },
    ],
    fotos: ['colheita'],
    videos: [
      {
        id: 'mOGuts7XDcM',
        canal: 'Plantamemo',
        tema: 'Tricomas',
        duracao: '4:01',
        titulo: 'TRICOMAS e o Ponto EXATO para a COLHEITA',
      },
      {
        id: 'bJXVJ43HYVM',
        canal: 'Plantamemo',
        tema: 'Quando colher',
        duracao: '3:41',
        titulo: 'Últimos Dias de FLORA, quando devo COLHER?',
      },
      {
        id: 'bj97cH4mwhM',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Erros',
        duracao: '45:23',
        titulo: 'COMO EVITAR OS ERROS NA HORA DA COLHEITA? NINGUÉM ENSINA ISSO!',
      },
    ],
    checklist: [
      'Observei os tricomas com lupa',
      'Decidi o ponto de colheita e anotei no diário',
      'Colhi as flores',
      'Preparei o local de secagem (escuro e ventilado)',
    ],
  },
  {
    id: 8,
    curto: 'Secar e curar',
    sub: 'cura de 2 a 4 sem',
    chips: ['Secagem + cura', '2 a 4 semanas de cura'],
    blocos: [
      {
        t: 'h',
        x: 'Secagem',
      },
      {
        t: 'p',
        x: 'Depois da colheita, as flores secam penduradas, num lugar escuro, com ar circulando e sem sol direto. Secar devagar preserva mais aroma do que secar depressa. Uma referência comum é de uma a duas semanas, até os galhinhos finos estalarem ao dobrar; o clima muda isso.',
      },
      {
        t: 'h',
        x: 'Cura (maturação)',
      },
      {
        t: 'p',
        x: 'Com as flores secas começa a **cura**, que costuma levar de 2 a 4 semanas, podendo ir além. É ela que melhora o aroma, o sabor e a conservação. Como fazer:',
      },
      {
        t: 'ol',
        itens: [
          'Use potes de vidro bem vedados, de preferência escuros, e deixe cerca de um terço do pote vazio.',
          'Na primeira semana, abra os potes algumas vezes por dia para renovar o ar; depois, uma vez por dia já basta.',
          'Cheire e apalpe as flores a cada abertura. Cheiro azedo ou de mofo é sinal de que ainda há umidade demais.',
          'Deixe os potes em lugar fresco, longe do calor e da luz.',
          'Para guardar por meses, embalagem a vácuo, no escuro e num lugar fresco.',
        ],
      },
      {
        t: 'nota',
        tipo: 'gastando',
        x: 'potes de vidro de conserva, bem lavados e secos, servem. Se forem transparentes, guarde-os dentro de um armário escuro.',
      },
      {
        t: 'nota',
        tipo: 'erro',
        x: 'fechar os potes e esquecer. Sem abrir, a umidade fica presa e aparece o mofo.',
      },
      {
        t: 'p',
        x: 'E depois? O preparo do óleo e o uso são assunto de um guia próprio, que vem a seguir.',
      },
    ],
    fotos: ['secar', 'curar'],
    videos: [
      {
        id: 'Z8qvl4zHH4Y',
        canal: 'Leds Indoor',
        tema: 'Secagem',
        duracao: '7:29',
        titulo: 'Como Secar a Cannabis Corretamente: Preserve Terpenos, Aroma e Potência',
      },
      {
        id: 'qGRABnEcsmQ',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Secagem barata',
        duracao: '16:28',
        titulo:
          'SECAGEM DA GANJA A BAIXO CUSTO: Como fazer, principais erros, temperatura, umidade e ventilação',
      },
      {
        id: 'q6Jk7znW63I',
        canal: 'Leds Indoor',
        tema: 'Cura',
        duracao: '6:46',
        titulo: 'Cura de Buds de Cannabis: Guia Completo para Aroma e Potência Máximos',
      },
      {
        id: '8nmn_STCy9A',
        canal: 'Natural TV (Natural Mystic Ganja)',
        tema: 'Cura e trima',
        duracao: '15:54',
        titulo: 'Cura e Trima na Prática: Passo a Passo Completo',
      },
    ],
    checklist: [
      'Pendurei as flores para secar em local escuro e ventilado',
      'Passei para potes de vidro (até 70% cheios)',
      'Abro os potes todo dia e confiro a umidade',
      'Guardo em lugar fresco, escuro e longe do calor',
    ],
  },
];

/** Lojas por item. O weediário não vende nem recebe comissão: são links diretos, sem rastreio. */
export const LOJAS: Record<string, Loja[]> = {
  sementes: [
    {
      loja: 'Flora Urbana',
      nome: 'Sementes de coleção de vários bancos; filtros por feminizada, regular e automática',
      preco: 'R$ 131',
      nota: 'menor preço dos destaques da página inicial',
      url: 'https://floraurbana420.com.br/',
    },
    {
      loja: 'NetSeeds',
      nome: 'Sementes de bancos conhecidos, em embalagem original; frete grátis a partir de R$ 699',
      url: 'https://www.netseeds.shop/',
    },
  ],
  armario: [
    {
      loja: 'Cultlight',
      nome: 'Estufa Garden HighPro BASIC 60x60x160 cm (2 a 6 plantas)',
      preco: 'R$ 579,60',
      url: 'https://cultlight.com.br/loja/estufas/estufa-basic-60x-60x-160cm-garden-highpro-cultivo-indoor/',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Cabine de Cultivo Elite E60, 60x60x160 cm',
      preco: 'R$ 595,56',
      url: 'https://www.ledsindoor.com.br/cabine-de-cultivo-leds-indoor-elite-e60',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar estufa de cultivo 60x60',
      url: 'https://shopee.com.br/search?keyword=estufa%20de%20cultivo%2060x60',
    },
  ],
  luz: [
    {
      loja: 'Cultlight',
      nome: 'LED 65W Samsung PRO (2 a 4 plantas)',
      preco: 'R$ 414,00',
      url: 'https://cultlight.com.br/loja/iluminacao/essencial/quantum-board-65w-samsung-lm281b-led-cultivo-indoor/',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Painel Quantum Board 60 W, Samsung LM301H, bivolt',
      preco: 'R$ 617,44',
      url: 'https://www.ledsindoor.com.br/painel-de-led-quantum-board-leds-indoor-qb60watts-samsung-lm301h-bivolt-lampada-para-cultivo-indoor',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar painel LED quantum board para cultivo',
      url: 'https://shopee.com.br/search?keyword=painel%20led%20quantum%20board%20cultivo',
    },
  ],
  timer: [
    {
      loja: 'Cultlight',
      nome: 'Timer analógico de 24 horas, bivolt',
      preco: 'R$ 36,80',
      url: 'https://cultlight.com.br/loja/acessorios/automacao-e-medidores/timer-analogico-24-horas-127v-220v-cultivo-indoor/',
    },
    {
      loja: 'Cultlight',
      nome: 'Timer digital de 24 horas, bivolt',
      preco: 'R$ 55,20',
      url: 'https://cultlight.com.br/loja/acessorios/automacao-e-medidores/timer-digital-bivolt-cultivo-indoor/',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Timer analógico mecânico de tomada, bivolt',
      preco: 'R$ 36,26',
      url: 'https://www.ledsindoor.com.br/timer-temporizador-analogico-mecanico-tomada-bivolt-110v220v',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar timer de tomada 24 horas',
      url: 'https://shopee.com.br/search?keyword=timer%20tomada%2024%20horas',
    },
  ],
  ventilacao: [
    {
      loja: 'LedsIndoor',
      nome: 'Exaustor Fan para cultivo, 100, 125 ou 150 mm',
      preco: 'R$ 86,38',
      nota: 'no Pix, a partir de',
      url: 'https://www.ledsindoor.com.br/exaustor-fan-para-cultivo-indoor-leds-indoor-100mm-125mm-150mm',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar cooler ou mini ventilador 12 V',
      url: 'https://shopee.com.br/search?keyword=cooler%20ventilador%2012v',
    },
  ],
  termo: [
    {
      loja: 'Cultlight',
      nome: 'Termo-higrômetro Cultlight',
      preco: 'R$ 29,44',
      url: 'https://cultlight.com.br/loja/acessorios/automacao-e-medidores/termo-higrometro-cultlight/',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Termo-higrômetro digital com sensor e relógio',
      preco: 'R$ 36,99',
      url: 'https://www.ledsindoor.com.br/termo-higrmetro-digital-leds-indoor-medidor-com-sensor-relogio',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar termo-higrômetro digital',
      url: 'https://shopee.com.br/search?keyword=termo%20higrometro%20digital',
    },
  ],
  vasos: [
    {
      loja: 'LedsIndoor',
      nome: 'Vaso de feltro com alças (vários tamanhos)',
      preco: 'R$ 13,60',
      nota: 'no Pix, a partir de',
      url: 'https://www.ledsindoor.com.br/vaso-de-feltro-leds-indoor-com-anilhas-e-alcas-cultivo-e-jardinagem',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Vaso de plástico quadrado anti-stress (5, 7 e 10 L)',
      preco: 'R$ 16,16',
      nota: 'no Pix, a partir de',
      url: 'https://www.ledsindoor.com.br/vasos/vaso-de-plastico-para-plantas-anti-stress-mudas-cultivo',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar vaso de tecido geotêxtil',
      url: 'https://shopee.com.br/search?keyword=vaso%20geotextil%20tecido',
    },
  ],
  substrato: [
    {
      loja: 'LedsIndoor',
      nome: 'Pó de Coco Premium Carolina Soil (10 L ou 45 L)',
      preco: 'R$ 24,28',
      nota: 'no Pix, a partir de',
      url: 'https://www.ledsindoor.com.br/po-de-coco-premium-2801-carolina-soil-45l',
    },
    {
      loja: 'LedsIndoor',
      nome: 'Substrato 50% turfa de sphagnum + 50% perlita (10 L ou 45 L)',
      preco: 'R$ 26,99',
      nota: 'no Pix, a partir de',
      url: 'https://www.ledsindoor.com.br/50-turfa-de-sphagnum-e-50-perlita-carolina-soil-45l',
    },
    {
      loja: 'Shopee',
      nome: 'Buscar fibra de coco e perlita',
      url: 'https://shopee.com.br/search?keyword=fibra%20de%20coco%20perlita',
    },
  ],
  ph: [
    {
      loja: 'Shopee',
      nome: 'Buscar medidor de pH (caneta ou fita)',
      url: 'https://shopee.com.br/search?keyword=medidor%20ph%20caneta',
    },
  ],
  lupa: [
    {
      loja: 'Shopee',
      nome: 'Buscar lupa 20x de joalheiro',
      url: 'https://shopee.com.br/search?keyword=lupa%2020x%20joalheiro',
    },
  ],
};

export const ROTULOS_LOJAS: Record<string, string> = {
  sementes: 'Onde comprar sementes',
  armario: 'Estufa ou tenda de cultivo',
  luz: 'Luz (painel de LED)',
  timer: 'Timer de tomada',
  ventilacao: 'Ventilação',
  termo: 'Termo-higrômetro',
  vasos: 'Vasos',
  substrato: 'Substrato',
  ph: 'Medidor de pH',
  lupa: 'Lupa para os tricomas',
};

export const COMPRAS: readonly ItemCompra[] = [
  {
    chave: 'armario',
    item: 'Espaço (tenda)',
    barata: 'Armário, sapateira ou caixa forrada',
    quando: 'Quando for ter mais de 3 plantas ou quiser controlar bem a luz',
  },
  {
    chave: 'luz',
    item: 'Luz',
    barata: 'LED branco para a fase inicial; fluorescente também serve na plântula',
    quando: 'Para a floração vale um painel de LED próprio para cultivo',
  },
  {
    chave: 'timer',
    item: 'Timer de tomada',
    barata: 'Timer comum de tomada',
    quando: 'Sempre: é barato e evita o erro mais comum de luz',
  },
  {
    chave: 'termo',
    item: 'Termo-higrômetro',
    barata: 'Modelo simples de bancada',
    quando: 'Sempre: mostra temperatura e umidade',
  },
  {
    chave: 'sementes',
    item: 'Sementes',
    barata: 'Semente de prensado, para quem quer começar sem gastar',
    quando: 'Variedade conhecida de um banco de sementes, se o objetivo é tratamento',
  },
  {
    chave: 'ventilacao',
    item: 'Ventilação',
    barata: 'Cooler de PC ou ventilador doméstico',
    quando: 'Exaustor, se o calor ou o cheiro forem problema',
  },
  {
    chave: 'substrato',
    item: 'Substrato',
    barata: 'Mistura própria (fibra de coco, perlita, húmus)',
    quando: 'Terra pronta para cultivo, se não quiser misturar',
  },
  {
    chave: 'vasos',
    item: 'Vasos',
    barata: 'Baldes e garrafas grandes, furados',
    quando: 'Vasos de tecido, se quiser mais drenagem',
  },
  {
    chave: 'lupa',
    item: 'Lupa',
    barata: 'Câmera macro do celular',
    quando: 'Lupa de joalheiro para ver os tricomas',
  },
  {
    chave: 'ph',
    item: 'Medidor de pH',
    barata: 'Fita ou kit simples',
    quando: 'Medidor digital, quando já tiver ciclo anotado',
  },
];

/** Data em que os preços de LOJAS foram conferidos nas páginas das lojas (preço no Pix). */
export const PRECOS_EM = '06/11/2026';
