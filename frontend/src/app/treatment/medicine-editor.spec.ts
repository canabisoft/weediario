import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Medicine } from '../core/api/treatment.api';
import { MedicineEditor } from './medicine-editor';

const OIL: Medicine = {
  id: 'b1', name: 'Óleo 20:1', form: 'OIL', origin: 'ASSOCIATION', thc: 1, cbd: 20, concentrationUnit: 'MG_PER_ML',
  stockUnit: 'ML', doseUnits: ['DROP', 'ML'], dropsPerMl: 20, initialQuantity: 30, remaining: 12, expired: false,
  expiresSoon: false, expiresOn: '2027-01-31', archived: false,
};

function setup(medicine: Medicine | null = null) {
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  const fixture = TestBed.createComponent(MedicineEditor);
  fixture.componentRef.setInput('patientId', 'p1');
  fixture.componentRef.setInput('medicine', medicine);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const type = (id: string, value: string) => {
    const field = root.querySelector<HTMLInputElement>(`#${id}`)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  return { fixture, root, type, http: TestBed.inject(HttpTestingController) };
}

describe('MedicineEditor', () => {
  it('saves a concentration typed in percent as mg/ml', () => {
    const { root, type, http, fixture } = setup();
    type('med-new-name', 'Óleo 3%');
    type('med-new-qty', '30');
    root.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1].click();
    fixture.detectChanges();
    type('med-new-cbd', '3');
    type('med-new-thc', '0,2');

    expect(root.textContent).toContain('= 30 mg/ml');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    const request = http.expectOne('/api/v1/me/treatment/patients/p1/medicines');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toMatchObject({ cbd: 30, thc: 2, initialQuantity: 30, form: 'OIL' });
  });

  it('fills the concentration from the flower used in a homemade oil', () => {
    const { root, type, fixture } = setup();
    type('med-new-flower', '10');
    type('med-new-oil', '30');
    type('med-new-flower-thc', '15');
    type('med-new-flower-cbd', '5');

    expect(root.textContent).toContain('50 mg/ml de THC');
    [...root.querySelectorAll('button')].find((b) => b.textContent!.includes('Usar estes valores'))!.click();
    fixture.detectChanges();

    expect(root.querySelector<HTMLInputElement>('#med-new-thc')!.value).toBe('50');
    expect(root.querySelector<HTMLInputElement>('#med-new-cbd')!.value).toBe('16,667');
    expect(root.querySelector<HTMLInputElement>('#med-new-qty')!.value).toBe('30');
  });

  it('edits the label with PUT, keeping form and initial quantity locked', () => {
    const { root, type, http } = setup(OIL);
    expect(root.querySelector<HTMLInputElement>('#med-b1-name')!.value).toBe('Óleo 20:1');
    expect(root.querySelector<HTMLSelectElement>('#med-b1-form')!.disabled).toBe(true);
    expect(root.querySelector<HTMLInputElement>('#med-b1-qty')!.disabled).toBe(true);

    type('med-b1-cbd', '25');
    type('med-b1-thc', '');
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    const request = http.expectOne('/api/v1/me/treatment/patients/p1/medicines/b1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ name: 'Óleo 20:1', cbd: 25, initialQuantity: 30, expiresOn: '2027-01-31' });
    expect(request.request.body.thc).toBeUndefined();
  });
});
