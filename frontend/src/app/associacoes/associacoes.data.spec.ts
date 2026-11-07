import {
  ASSOCIACOES,
  CATEGORIAS,
  UFS,
  filtrar,
  linkEmail,
  linkTelefone,
  linkWhatsapp,
  listar,
  mensagem,
} from './associacoes.data';

describe('associações', () => {
  it('só traz dado institucional público: produto só como categoria genérica, sem preço', () => {
    for (const a of ASSOCIACOES) {
      expect(Object.keys(a).sort()).toEqual([
        'categorias',
        'cidade',
        'email',
        'logo',
        'nome',
        'outrosEstados',
        'resumo',
        'site',
        'slug',
        'telefone',
        'uf',
        'whatsapp',
      ]);
      expect(a.site).toMatch(/^https:\/\//);
      expect(a.uf).toMatch(/^[A-Z]{2}$/);
    }
    expect(new Set(ASSOCIACOES.map((a) => a.slug)).size).toBe(ASSOCIACOES.length);
  });

  it('põe as do estado primeiro e filtra por estado, mantendo as que atendem outros estados', () => {
    expect(listar('PB')[0].slug).toBe('abrace-esperanca');
    const pb = filtrar('PB');
    expect(pb.every((a) => a.uf === 'PB' || a.outrosEstados === true)).toBe(true);
    expect(pb.some((a) => a.slug === 'ama-me')).toBe(true);
    expect(filtrar('')).toHaveLength(ASSOCIACOES.length);
  });

  it('filtra por categoria só entre as que a informaram, e nunca expõe nome de produto', () => {
    expect(filtrar('', 'pomada').map((a) => a.slug)).toEqual(['abrace-esperanca']);
    expect(filtrar('SP', 'pomada')).toHaveLength(0);
    expect(filtrar('PB', 'oleo').length).toBeGreaterThan(0);
    for (const a of ASSOCIACOES) {
      for (const c of a.categorias) {
        expect(Object.keys(CATEGORIAS)).toContain(c);
      }
    }
    expect(Object.values(CATEGORIAS).join(' ')).not.toMatch(/mg|THC|CBD|R\$/i);
  });

  it('os contatos têm formato válido (e-mail simples, WhatsApp 55+DDD+número, logo sem caminho)', () => {
    for (const a of ASSOCIACOES) {
      if (a.email) {
        expect(a.email, a.slug).toMatch(/^[^\s@%]+@[^\s@%]+\.[^\s@%]+$/);
      }
      if (a.whatsapp) {
        expect(a.whatsapp, a.slug).toMatch(/^55\d{10,11}$/);
      }
      if (a.logo) {
        expect(a.logo, a.slug).toMatch(/^[\w-]+\.(png|jpg|webp|svg)$/);
      }
    }
  });

  it('lista os estados sem repetir', () => {
    expect(UFS).toContain('PB');
    expect(new Set(UFS).size).toBe(UFS.length);
  });

  it('o contato sai com a mensagem "vim do weediario.com", em link direto', () => {
    const abrace = ASSOCIACOES.find((a) => a.slug === 'abrace-esperanca')!;
    expect(mensagem(abrace)).toBe(
      'Olá! Vim do weediario.com e gostaria de saber mais sobre a Abrace Esperança.',
    );
    expect(linkWhatsapp(abrace)).toContain('https://wa.me/558398720072?text=');
    expect(linkWhatsapp({ ...abrace, whatsapp: null })).toBeNull(); // sem número, não inventa
    expect(linkEmail(abrace)).toContain(
      'mailto:contato@abraceesperanca.org.br?subject=Vim%20do%20weediario.com&body=',
    );
    expect(linkTelefone(abrace)).toBe('tel:08003132367');

    const wa = linkWhatsapp({ ...abrace, whatsapp: '5583999990000' })!;
    expect(wa.startsWith('https://wa.me/5583999990000?text=')).toBe(true);
    expect(decodeURIComponent(wa)).toContain('Vim do weediario.com');
  });
});
