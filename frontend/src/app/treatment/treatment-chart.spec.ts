import { TestBed } from '@angular/core/testing';
import { Series } from '../core/api/treatment.api';
import { TreatmentChart, barPath, niceCeiling } from './treatment-chart';

const series: Series = {
  from: '2026-09-28',
  to: '2026-09-30',
  symptoms: [
    { id: 'pain', name: 'Dor', kind: 'SCALE', archived: false },
    { id: 'seizures', name: 'Crises', kind: 'COUNT', archived: false },
    { id: 'unused', name: 'Sem registro', kind: 'SCALE', archived: false },
  ],
  days: [
    { day: '2026-09-28', doses: 2, cbdMg: 20, thcMg: 1, symptoms: { pain: 7, seizures: 2 } },
    { day: '2026-09-29', doses: 0, symptoms: {} },
    { day: '2026-09-30', doses: 1, cbdMg: 10, thcMg: 0.5, symptoms: { pain: 4 } },
  ],
};

describe('TreatmentChart', () => {
  it('draws separate aligned charts (never a second y axis), a legend and a table view', async () => {
    const fixture = TestBed.createComponent(TreatmentChart);
    fixture.componentRef.setInput('series', series);
    Object.defineProperty(fixture.nativeElement, 'clientWidth', { value: 480 });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    const legend = root.querySelector('[aria-label="Legenda"]')!.textContent!;
    expect(legend).toContain('CBD (mg)');
    expect(legend).toContain('Dor (0–10)');
    expect(legend).toContain('Crises (contagem)');
    expect(legend).not.toContain('Sem registro');

    // mg/dia, notas 0–10 e contagem: três gráficos, cada um com o próprio eixo.
    expect(root.querySelectorAll('svg').length).toBe(3);

    const rows = root.querySelectorAll('details tbody tr');
    expect(rows.length).toBe(2); // dia sem registro fica fora da tabela
    expect(rows[0].textContent).toContain('20 mg');
  });

  it('rounds only the top of a bar and picks round axis tops', () => {
    expect(barPath(0, 100, 10, 50, false)).toBe('M0,100 L0,50  L10,50  L10,100 Z');
    expect(niceCeiling(21)).toBe(50);
    expect(niceCeiling(0)).toBe(10);
  });
});
