import { TestBed } from '@angular/core/testing';
import { GuiaEstado } from './guia-estado';

const CHAVE = 'wd-guia-cultivo';

function novo(): GuiaEstado {
  TestBed.resetTestingModule();
  return TestBed.inject(GuiaEstado);
}

describe('GuiaEstado', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('começa vazio', () => {
    const estado = novo();

    expect(estado.feitas().size).toBe(0);
    expect(estado.atual()).toBeNull();
  });

  it('marca e desmarca um item e guarda no aparelho', () => {
    const estado = novo();

    estado.alternar(2, 1);
    expect(estado.marcado(2, 1)).toBe(true);
    expect(JSON.parse(localStorage.getItem(CHAVE)!).feitas).toEqual(['2-1']);

    estado.alternar(2, 1);
    expect(estado.marcado(2, 1)).toBe(false);
    expect(JSON.parse(localStorage.getItem(CHAVE)!).feitas).toEqual([]);
  });

  it('lembra o que foi marcado na próxima visita', () => {
    const primeira = novo();
    primeira.alternar(0, 0);
    primeira.alternar(5, 3);
    primeira.definirAtual(5);

    const segunda = novo();

    expect(segunda.marcado(0, 0)).toBe(true);
    expect(segunda.marcado(5, 3)).toBe(true);
    expect(segunda.atual()).toBe(5);
  });

  it('limpar apaga o progresso', () => {
    const estado = novo();
    estado.alternar(1, 1);
    estado.definirAtual(1);

    estado.limpar();

    expect(estado.feitas().size).toBe(0);
    expect(estado.atual()).toBeNull();
    expect(novo().feitas().size).toBe(0);
  });

  it('trata o que está no armazenamento como não confiável', () => {
    localStorage.setItem(
      CHAVE,
      JSON.stringify({ feitas: ['1-2', '<script>', 7, '99-99-9', '3-1'], atual: 42 }),
    );

    const estado = novo();

    expect([...estado.feitas()].sort()).toEqual(['1-2', '3-1']);
    expect(estado.atual()).toBeNull();
  });

  it('texto corrompido não quebra a página', () => {
    localStorage.setItem(CHAVE, '{não é json');

    expect(() => novo()).not.toThrow();
    expect(novo().feitas().size).toBe(0);
  });

  it('sem armazenamento (modo privado) o progresso vale na sessão e nada quebra', () => {
    const estado = novo();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('cheio');
    });

    expect(() => estado.alternar(4, 0)).not.toThrow();
    expect(estado.marcado(4, 0)).toBe(true);
  });
});
