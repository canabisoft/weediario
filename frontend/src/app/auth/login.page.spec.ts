import { safeReturnUrl } from './login.page';

describe('safeReturnUrl', () => {
  it('accepts internal paths', () => {
    expect(safeReturnUrl('/diarios/123')).toBe('/diarios/123');
  });

  it('rejects external or protocol-relative URLs (open redirect)', () => {
    expect(safeReturnUrl('https://phishing.example')).toBe('/diarios');
    expect(safeReturnUrl('//phishing.example')).toBe('/diarios');
    expect(safeReturnUrl('javascript:alert(1)')).toBe('/diarios');
    expect(safeReturnUrl(undefined)).toBe('/diarios');
  });
});
