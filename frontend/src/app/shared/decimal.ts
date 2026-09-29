/**
 * Lê números digitados em português ("25,5") ou em inglês ("25.5"). Retorna null se o texto não for um número.
 */
export function parseDecimal(text: string | null | undefined): number | null {
  if (text == null) {
    return null;
  }
  const normalized = text.trim().replace(',', '.');
  if (normalized === '' || !/^-?\d*\.?\d+$|^-?\d+\.$/.test(normalized)) {
    return null;
  }
  const value = Number(normalized);
  return Number.isFinite(value) ? value : null;
}

const pt = new Intl.NumberFormat('pt-BR');

export function formatDecimal(value: number, decimals: number): string {
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(
    value,
  );
}

/** Até {@code maxDecimals} casas, só quando necessárias: 120 → "120", 120.5 → "120,5". */
export function formatCompact(value: number, maxDecimals = 1): string {
  return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: maxDecimals }).format(value);
}

export function formatInteger(value: number): string {
  return pt.format(Math.round(value));
}
