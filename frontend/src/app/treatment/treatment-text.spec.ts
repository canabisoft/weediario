import { Medicine } from '../core/api/treatment.api';
import { acceptsPercent, concentration, decimalInput, homemadeMgPerMl, percentToMg, previewMg } from './treatment-text';

const oil: Medicine = {
  id: 'b1',
  name: 'Óleo 20:1',
  form: 'OIL',
  origin: 'ASSOCIATION',
  thc: 1,
  cbd: 20,
  concentrationUnit: 'MG_PER_ML',
  stockUnit: 'ML',
  doseUnits: ['DROP', 'ML'],
  dropsPerMl: 20,
  initialQuantity: 30,
  remaining: 30,
  expired: false,
  expiresSoon: false,
  archived: false,
};

describe('treatment text', () => {
  it('previews the mg of what was used exactly like the backend computes', () => {
    expect(previewMg(oil, 10, 'DROP')).toEqual({ thc: 0.5, cbd: 10 });
    expect(previewMg(oil, 1, 'ML')).toEqual({ thc: 1, cbd: 20 });
    const flower = { ...oil, form: 'FLOWER' as const, concentrationUnit: 'PERCENT' as const, thc: 20, cbd: undefined };
    expect(previewMg(flower, 0.5, 'G')).toEqual({ thc: 100, cbd: undefined });
    expect(previewMg(oil, 1, 'APPLICATION')).toEqual({});
  });

  it('describes the label concentration', () => {
    expect(concentration(oil)).toBe('THC 1 mg/ml · CBD 20 mg/ml');
    expect(concentration({ ...oil, thc: undefined, cbd: undefined })).toBe('Concentração não informada');
  });
});

describe('concentration in percent', () => {
  it('accepts percent only where the label unit is mg per ml or per g', () => {
    expect(acceptsPercent('OIL')).toBe(true);
    expect(acceptsPercent('TINCTURE')).toBe(true);
    expect(acceptsPercent('TOPICAL')).toBe(true);
    expect(acceptsPercent('FLOWER')).toBe(false); // já é em %
    expect(acceptsPercent('GUMMY')).toBe(false);
    expect(acceptsPercent('CAPSULE')).toBe(false);
  });

  it('converts 1% to 10 mg/ml', () => {
    expect(percentToMg(3)).toBe(30);
    expect(percentToMg(0.2)).toBe(2);
  });

  it('estimates homemade oil from the flower', () => {
    // 10 g de flor a 15% = 1500 mg de THC em 30 ml de óleo.
    expect(homemadeMgPerMl(10, 15, 30)).toBe(50);
    expect(homemadeMgPerMl(10, 5, 30)).toBe(16.667);
    expect(homemadeMgPerMl(0, 15, 30)).toBeNull();
    expect(homemadeMgPerMl(10, 15, 0)).toBeNull();
    expect(homemadeMgPerMl(10, 120, 30)).toBeNull();
  });

  it('writes numbers for inputs without thousands separator', () => {
    expect(decimalInput(1500)).toBe('1500');
    expect(decimalInput(16.6666)).toBe('16,667');
    expect(decimalInput(undefined)).toBe('');
  });
});
