import { Medicine } from '../core/api/treatment.api';
import { concentration, previewMg } from './treatment-text';

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
