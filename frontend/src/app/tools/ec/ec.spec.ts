import { convert, convertToAll } from './ec';

describe('EC / PPM', () => {
  it('converts EC to every scale', () => {
    expect(convertToAll(1.2, 'ec')).toEqual({
      ec: 1.2,
      us: 1200,
      cf: 12,
      ppm500: 600,
      ppm640: 768,
      ppm700: 840,
    });
  });

  it('converts between PPM scales through EC', () => {
    expect(convert(1000, 'ppm500', 'ppm700')).toBe(1400);
    expect(convert(1400, 'ppm700', 'ec')).toBe(2);
  });

  it('rounds each scale to its usual precision', () => {
    expect(convert(333, 'ppm500', 'ec')).toBe(0.67);
    expect(convert(333, 'ppm500', 'cf')).toBe(6.7);
  });

  it('rejects negative values and implausible readings', () => {
    expect(() => convert(-1, 'ec', 'ppm500')).toThrowError(RangeError);
    expect(() => convert(50, 'ec', 'ppm500')).toThrowError(/confira a unidade/);
  });
});
