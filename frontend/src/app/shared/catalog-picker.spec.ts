import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CatalogDbApi, CatalogItem, Resolution } from '../core/api/catalog-db.api';
import { CatalogPicker } from './catalog-picker';

describe('CatalogPicker', () => {
  const bubba: CatalogItem = { id: 'b1', name: 'Bubba Kush', parentName: 'Pheno Finder', status: 'VERIFIED', source: 'DATASET', usage: 3 };
  const resolveCalls: { name: string; confirmNew: boolean }[] = [];
  let nextResolution: Resolution;

  const api: Partial<CatalogDbApi> = {
    suggest: () => of([bubba]),
    resolve: (_kind, body) => {
      resolveCalls.push({ name: body.name, confirmNew: body.confirmNew });
      return of(nextResolution);
    },
  };

  beforeEach(() => {
    resolveCalls.length = 0;
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [{ provide: CatalogDbApi, useValue: api }] });
  });

  afterEach(() => vi.useRealTimers());

  function render() {
    const fixture = TestBed.createComponent(CatalogPicker);
    fixture.componentRef.setInput('kind', 'strains');
    fixture.componentRef.setInput('label', 'Strain');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = (text: string) => {
      const input = root.querySelector('input')!;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(300);
      fixture.detectChanges();
    };
    return { fixture, root, type };
  }

  it('suggests catalog items with breeder and source, plus an add option', () => {
    const { root, type } = render();

    type('bubb');

    const options = Array.from(root.querySelectorAll('[role=option]')).map((el) => el.textContent!.replace(/\s+/g, ' ').trim());
    expect(options[0]).toContain('Bubba Kush');
    expect(options[0]).toContain('Pheno Finder');
    expect(options[0]).toContain('Base pública');
    expect(options.at(-1)).toContain('Adicionar "bubb"');
    expect(root.querySelector('input')!.getAttribute('aria-expanded')).toBe('true');
  });

  it('picking a suggestion sets the value', () => {
    const { fixture, root, type } = render();
    type('bubb');

    root.querySelector<HTMLElement>('[role=option]')!.dispatchEvent(new MouseEvent('mousedown'));
    fixture.detectChanges();

    expect(fixture.componentInstance.value()).toEqual(bubba);
    expect(root.textContent).toContain('Bubba Kush');
  });

  it('asks "did you mean?" before creating a near-duplicate, then creates on confirmation', () => {
    const { fixture, root, type } = render();
    type('Buba Kush');
    nextResolution = { outcome: 'SIMILAR', candidates: [bubba] };

    root.querySelectorAll<HTMLElement>('[role=option]')[1].dispatchEvent(new MouseEvent('mousedown'));
    fixture.detectChanges();

    expect(root.querySelector('[role=alert]')!.textContent).toContain('Você quis dizer');
    expect(fixture.componentInstance.value()).toBeNull();

    const created: CatalogItem = { id: 'new', name: 'Buba Kush', status: 'UNVERIFIED', source: 'COMMUNITY' };
    nextResolution = { outcome: 'CREATED', item: created, candidates: [] };
    Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.includes('Não, é outro'))!.click();
    fixture.detectChanges();

    expect(resolveCalls).toEqual([
      { name: 'Buba Kush', confirmNew: false },
      { name: 'Buba Kush', confirmNew: true },
    ]);
    expect(fixture.componentInstance.value()).toEqual(created);
  });
});
