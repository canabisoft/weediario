import {
  Bloco,
  COMPRAS,
  ETAPAS,
  FOTOS,
  LOJAS,
  PRECOS_EM,
  ROTULOS_LOJAS,
} from './guia-cultivo.data';
import { ETAPAS_RESUMO } from './guia-cultivo.resumo';
import { trechos } from './guia-texto';

/** Todos os textos do bloco, inclusive os de dentro de "caixa". */
function textos(blocos: readonly Bloco[]): string[] {
  const saida: string[] = [];
  for (const b of blocos) {
    switch (b.t) {
      case 'p':
      case 'h':
        saida.push(b.x);
        break;
      case 'ul':
      case 'ol':
        saida.push(...b.itens);
        break;
      case 'tabela':
        saida.push(...b.cab, ...b.linhas.flat());
        break;
      case 'nota':
        saida.push(b.x);
        break;
      case 'caixa':
        saida.push(b.titulo, ...textos(b.blocos));
        break;
      case 'receita':
        saida.push(b.titulo, ...b.itens);
        break;
      case 'link':
        saida.push(b.x);
        break;
      case 'lojas':
        break;
    }
  }
  return saida;
}

function blocosDoTipo<T extends Bloco['t']>(
  blocos: readonly Bloco[],
  tipo: T,
): Extract<Bloco, { t: T }>[] {
  const saida: Extract<Bloco, { t: T }>[] = [];
  for (const b of blocos) {
    if (b.t === tipo) {
      saida.push(b as Extract<Bloco, { t: T }>);
    }
    if (b.t === 'caixa') {
      saida.push(...blocosDoTipo(b.blocos, tipo));
    }
  }
  return saida;
}

describe('conteúdo do guia de cultivo', () => {
  it('tem as 9 etapas, de 0 a 8, em ordem', () => {
    expect(ETAPAS.map((e) => e.id)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
  });

  it('cada etapa tem texto e um checklist de verdade', () => {
    for (const e of ETAPAS) {
      expect(e.curto.length, `curto da etapa ${e.id}`).toBeGreaterThan(2);
      expect(e.blocos.length, `blocos da etapa ${e.id}`).toBeGreaterThanOrEqual(5);
      expect(e.checklist.length, `checklist da etapa ${e.id}`).toBeGreaterThanOrEqual(3);
      expect(new Set(e.checklist).size).toBe(e.checklist.length);
    }
  });

  it('o resumo do SEO tem os mesmos nomes das etapas', () => {
    expect(ETAPAS_RESUMO.map((r) => r.nome)).toEqual(ETAPAS.map((e) => e.curto));
    expect(ETAPAS_RESUMO.map((r) => r.descricao)).toEqual(ETAPAS.map((e) => e.sub));
  });

  it('nenhum texto tem HTML: a marcação é só **, * e [](link)', () => {
    for (const e of ETAPAS) {
      for (const t of textos(e.blocos)) {
        expect(t, `etapa ${e.id}: ${t.slice(0, 40)}`).not.toMatch(/[<>]|&[a-z#0-9]+;/i);
      }
    }
  });

  it('todo link do texto é https ou interno', () => {
    for (const e of ETAPAS) {
      const links = [
        ...textos(e.blocos)
          .flatMap((t) => trechos(t))
          .filter((p) => p.href)
          .map((p) => p.href!),
        ...blocosDoTipo(e.blocos, 'link').map((l) => l.href),
      ];
      for (const href of links) {
        expect(href, `etapa ${e.id}`).toMatch(/^(https:\/\/|\/(?!\/))/);
      }
    }
  });

  it('só usa fotos que existem na tabela de créditos, servidas do próprio site', () => {
    for (const e of ETAPAS) {
      for (const chave of e.fotos) {
        expect(FOTOS[chave], `foto "${chave}" da etapa ${e.id}`).toBeDefined();
      }
    }
    for (const [chave, f] of Object.entries(FOTOS)) {
      expect(f.arq).toBe(`/guia-cultivo/${chave}.jpg`);
      expect(f.origem).toMatch(/^https:\/\/commons\.wikimedia\.org\/wiki\/File:/);
      expect(f.autor.length).toBeGreaterThan(1);
      expect(f.licenca.length).toBeGreaterThan(1);
    }
  });

  it('os vídeos são só ids do YouTube (viram link; não há player nem miniatura)', () => {
    let total = 0;
    for (const e of ETAPAS) {
      const ids = e.videos.map((v) => v.id);
      expect(new Set(ids).size, `repetido na etapa ${e.id}`).toBe(ids.length);
      for (const v of e.videos) {
        expect(v.id).toMatch(/^[A-Za-z0-9_-]{11}$/);
        expect(v.titulo.length).toBeGreaterThan(5);
        expect(v.canal.length).toBeGreaterThan(2);
        expect(v.duracao).toMatch(/^\d{1,2}(:\d{2}){1,2}$/);
        total++;
      }
    }
    expect(total).toBeGreaterThanOrEqual(25);
  });

  it('as lojas citadas existem; links são https; preço tem o formato R$', () => {
    for (const e of ETAPAS) {
      for (const bloco of blocosDoTipo(e.blocos, 'lojas')) {
        for (const chave of bloco.itens) {
          expect(LOJAS[chave], `loja "${chave}"`).toBeDefined();
          expect(ROTULOS_LOJAS[chave], `rótulo de "${chave}"`).toBeDefined();
        }
      }
    }
    for (const [chave, lista] of Object.entries(LOJAS)) {
      for (const l of lista) {
        expect(l.url, chave).toMatch(/^https:\/\//);
        if (l.preco) {
          expect(l.preco).toMatch(/^R\$ \d{1,3}(\.\d{3})*(,\d{2})?$/);
        }
      }
    }
    expect(PRECOS_EM).toMatch(/^\d{2}\/\d{2}\/\d{4}$/);
  });

  it('a lista de compras só aponta para grupos de lojas que existem', () => {
    for (const c of COMPRAS) {
      expect(ROTULOS_LOJAS[c.chave], c.chave).toBeDefined();
    }
  });

  it('o aviso sobre as 6 plantas não diz que plantar é liberado', () => {
    const regularizacao = textos(ETAPAS[0].blocos).join(' ');

    expect(regularizacao).toContain('Não é autorização para plantar');
    expect(regularizacao).toMatch(/não é aconselhamento jurídico/i);
  });
});
