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

  it('rejects the tricks that pass a "starts with /" check (guide 3.22)', () => {
    // A barra invertida vira barra, e a tabulação some: os dois viram "//phishing.example".
    expect(safeReturnUrl('/\\phishing.example')).toBe('/diarios');
    expect(safeReturnUrl('/\\/phishing.example')).toBe('/diarios');
    expect(safeReturnUrl('/\t/phishing.example')).toBe('/diarios');
    // Codificada, a tabulação é só parte do caminho: continua no próprio site.
    expect(safeReturnUrl('/%09/phishing.example')).toBe('/%09/phishing.example');
  });

  it('keeps the query and the fragment of internal paths', () => {
    expect(safeReturnUrl('/strains?q=gelato#reviews')).toBe('/strains?q=gelato#reviews');
  });
});
