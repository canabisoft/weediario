import { TestBed } from '@angular/core/testing';
import { ASSOCIACOES } from './associacoes.data';
import { AssociacoesPage } from './associacoes.page';

describe('AssociacoesPage', () => {
  function render() {
    TestBed.resetTestingModule();
    const fixture = TestBed.createComponent(AssociacoesPage);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }
  const escondidos = (root: HTMLElement) => root.querySelectorAll('li.hidden').length;

  it('põe todas as associações no HTML, com link direto e sem rastreio', () => {
    const { root } = render();

    expect(root.querySelector('h1')?.textContent).toContain('Associações de cannabis medicinal');
    expect(root.querySelectorAll('li[data-uf]')).toHaveLength(ASSOCIACOES.length);
    for (const link of Array.from(
      root.querySelectorAll<HTMLAnchorElement>('ul li a[href^="https://"]'),
    )) {
      expect(link.rel).toContain('noopener');
      expect(link.href).toMatch(/^https:\/\//);
    }
    expect(root.textContent).toContain('Sem receita ainda?');
    expect(root.querySelector('img[src="/associacoes/abrace-esperanca.jpg"]')).not.toBeNull();
    expect(root.querySelectorAll('ul li img[src^="/guia-cultivo"]')).toHaveLength(0);
    expect(root.querySelector<HTMLAnchorElement>('a[href^="mailto:"]')?.href).toContain(
      'weediario.com',
    );
    expect(root.textContent).not.toMatch(/catálogo de produtos|R\$/);
  });

  it('o filtro por estado esconde as outras e avisa quando o estado não tem associação', () => {
    const { fixture, root } = render();
    const select = root.querySelector<HTMLSelectElement>('#assoc-uf')!;

    select.value = 'PB';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(escondidos(root)).toBeGreaterThan(0);
    expect(root.querySelector('li:not(.hidden) h3')?.textContent).toContain('Abrace Esperança');

    select.value = '';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(escondidos(root)).toBe(0);
  });
});
