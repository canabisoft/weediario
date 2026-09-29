import { formatDecimal, parseDecimal } from './decimal';

describe('parseDecimal', () => {
  it('accepts Brazilian and English decimal separators', () => {
    expect(parseDecimal('25,5')).toBe(25.5);
    expect(parseDecimal('25.5')).toBe(25.5);
    expect(parseDecimal(' 60 ')).toBe(60);
    expect(parseDecimal('-3')).toBe(-3);
    expect(parseDecimal(',5')).toBe(0.5);
  });

  it('returns null for anything that is not a plain number', () => {
    for (const invalid of ['', 'abc', '1e3', '25,5,1', '12a', null, undefined]) {
      expect(parseDecimal(invalid)).toBeNull();
    }
  });

  it('formats with the Brazilian separator', () => {
    expect(formatDecimal(1.2, 2)).toBe('1,20');
  });
});
