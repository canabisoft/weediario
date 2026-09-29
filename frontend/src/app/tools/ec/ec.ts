/**
 * Conversões entre as escalas de condutividade usadas em medidores de nutrientes.
 *
 * EC (mS/cm) é a medida física. PPM/TDS é uma <em>estimativa</em> derivada da EC com um fator que muda
 * de fabricante para fabricante; por isso "1000 ppm" em um medidor pode ser outro valor em outro.
 */

export type EcUnit = 'ec' | 'us' | 'cf' | 'ppm500' | 'ppm640' | 'ppm700';

export interface UnitInfo {
  readonly id: EcUnit;
  readonly label: string;
  readonly hint: string;
  /** Quantas unidades desta escala correspondem a 1 mS/cm. */
  readonly perMsCm: number;
  readonly decimals: number;
}

export const EC_UNITS: readonly UnitInfo[] = [
  { id: 'ec', label: 'EC (mS/cm)', hint: 'Medida física de referência', perMsCm: 1, decimals: 2 },
  { id: 'us', label: 'EC (µS/cm)', hint: 'Mesma medida, em microsiemens', perMsCm: 1000, decimals: 0 },
  { id: 'cf', label: 'CF', hint: 'Fator de condutividade (comum na Austrália/NZ)', perMsCm: 10, decimals: 1 },
  { id: 'ppm500', label: 'PPM 500', hint: 'Escala NaCl: Hanna, Milwaukee e maioria dos medidores dos EUA', perMsCm: 500, decimals: 0 },
  { id: 'ppm640', label: 'PPM 640', hint: 'Escala 640: alguns medidores Eutech', perMsCm: 640, decimals: 0 },
  { id: 'ppm700', label: 'PPM 700', hint: 'Escala KCl: Truncheon e medidores europeus/australianos', perMsCm: 700, decimals: 0 },
];

export const MAX_EC_MS_CM = 10;

export function toMsCm(value: number, unit: EcUnit): number {
  if (!Number.isFinite(value) || value < 0) {
    throw new RangeError('Informe um valor positivo');
  }
  const msCm = value / info(unit).perMsCm;
  if (msCm > MAX_EC_MS_CM) {
    throw new RangeError('Valor acima do que uma solução nutritiva costuma ter; confira a unidade');
  }
  return msCm;
}

export function convert(value: number, from: EcUnit, to: EcUnit): number {
  const target = info(to);
  const factor = 10 ** target.decimals;
  return Math.round(toMsCm(value, from) * target.perMsCm * factor) / factor;
}

export function convertToAll(value: number, from: EcUnit): Record<EcUnit, number> {
  return Object.fromEntries(EC_UNITS.map((unit) => [unit.id, convert(value, from, unit.id)])) as Record<EcUnit, number>;
}

function info(unit: EcUnit): UnitInfo {
  const found = EC_UNITS.find((candidate) => candidate.id === unit);
  if (!found) {
    throw new RangeError(`Unidade desconhecida: ${unit}`);
  }
  return found;
}

export interface EcGuideline {
  readonly stage: string;
  readonly minMsCm: number;
  readonly maxMsCm: number;
}

/**
 * Faixas gerais de EC da solução para cultivo mineral/inerte. Em solo vivo (orgânico) a planta se alimenta
 * pela biologia do solo e a EC da rega não é o parâmetro principal.
 */
export const EC_GUIDELINES: readonly EcGuideline[] = [
  { stage: 'Mudas e clones', minMsCm: 0.4, maxMsCm: 0.8 },
  { stage: 'Vegetativo', minMsCm: 0.8, maxMsCm: 1.4 },
  { stage: 'Início da flora', minMsCm: 1.2, maxMsCm: 1.8 },
  { stage: 'Pico da flora', minMsCm: 1.6, maxMsCm: 2.2 },
  { stage: 'Lavagem final', minMsCm: 0, maxMsCm: 0.4 },
];
