import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { GuiaTexto, trechos } from './guia-texto';

describe('guia-texto: marcação mínima', () => {
  it('separa texto, negrito, itálico e link', () => {
    const partes = trechos('Veja **isto** e *aquilo* em [o site](https://exemplo.com/a).');

    expect(partes).toEqual([
      { texto: 'Veja ' },
      { texto: 'isto', forte: true },
      { texto: ' e ' },
      { texto: 'aquilo', italico: true },
      { texto: ' em ' },
      { texto: 'o site', href: 'https://exemplo.com/a' },
      { texto: '.' },
    ]);
  });

  it('marca como interno o link que começa com "/"', () => {
    expect(trechos('[VPD](/ferramentas/vpd)')).toEqual([
      { texto: 'VPD', href: '/ferramentas/vpd', interno: true },
    ]);
  });

  it('só aceita link https:// ou interno: o resto vira texto puro', () => {
    for (const href of [
      'http://exemplo.com',
      'javascript:alert(1)',
      'data:text/html,x',
      '//outro.com/x',
      'ftp://x.com',
    ]) {
      const [parte] = trechos(`[clique](${href})`);
      expect(parte).toEqual({ texto: 'clique' });
    }
  });

  it('não interpreta HTML: a marcação "<b>" fica como texto', () => {
    expect(trechos('<b>oi</b> <script>x</script>')).toEqual([
      { texto: '<b>oi</b> <script>x</script>' },
    ]);
  });

  it('texto sem marcação volta inteiro', () => {
    expect(trechos('Só texto.')).toEqual([{ texto: 'Só texto.' }]);
    expect(trechos('')).toEqual([]);
  });
});

describe('GuiaTexto (como aparece na tela)', () => {
  function render(texto: string): HTMLElement {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(GuiaTexto);
    fixture.componentRef.setInput('texto', texto);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('não cria espaço sobrando entre o negrito e a pontuação', () => {
    const root = render('Precisa de **luz**, **vasos** e *ar*. Fim.');

    expect(root.textContent!.replace(/\s+/g, ' ').trim()).toBe('Precisa de luz, vasos e ar. Fim.');
  });

  it('renderiza link interno com routerLink e externo com rel seguro', () => {
    const root = render('Veja o [VPD](/ferramentas/vpd) e o [STF](https://noticias.stf.jus.br/x).');

    expect(root.querySelector('a[href="/ferramentas/vpd"]')).not.toBeNull();
    const externo = root.querySelector<HTMLAnchorElement>('a[href^="https://"]')!;
    expect(externo.getAttribute('rel')).toBe('noopener noreferrer');
    expect(externo.getAttribute('target')).toBe('_blank');
  });
});
