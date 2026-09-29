/**
 * Cálculo de VPD (déficit de pressão de vapor), em kPa.
 *
 * Usa a equação de Tetens para a pressão de saturação do vapor. O VPD "da folha" considera que a folha
 * costuma ficar um pouco mais fria que o ar (transpiração), por isso aceita a temperatura da folha medida
 * (termômetro infravermelho) ou um deslocamento em relação ao ar.
 */

export type VpdStage = 'propagation' | 'vegetative' | 'early-flower' | 'late-flower';

export interface VpdRange {
  readonly min: number;
  readonly max: number;
}

export interface StageInfo {
  readonly id: VpdStage;
  readonly label: string;
  readonly range: VpdRange;
}

/**
 * Faixas de referência de VPD da folha mais usadas no cultivo indoor. São um ponto de partida,
 * não regra: genética, luz e CO₂ mudam o ideal.
 */
export const VPD_STAGES: readonly StageInfo[] = [
  { id: 'propagation', label: 'Mudas e clones', range: { min: 0.4, max: 0.8 } },
  { id: 'vegetative', label: 'Vegetativo', range: { min: 0.8, max: 1.2 } },
  { id: 'early-flower', label: 'Início da flora', range: { min: 1.0, max: 1.5 } },
  { id: 'late-flower', label: 'Final da flora', range: { min: 1.2, max: 1.6 } },
];

export type VpdZone = 'low' | 'ideal' | 'high';

export interface VpdInput {
  /** Temperatura do ar em °C. */
  readonly airTempC: number;
  /** Umidade relativa do ar, 0–100. */
  readonly relativeHumidity: number;
  /** Temperatura da folha em °C. Se omitida, usa ar − {@link DEFAULT_LEAF_OFFSET_C}. */
  readonly leafTempC?: number;
}

export interface VpdResult {
  readonly leafVpd: number;
  readonly airVpd: number;
  readonly leafTempC: number;
}

export const DEFAULT_LEAF_OFFSET_C = 2;

export const LIMITS = {
  tempMin: -10,
  tempMax: 50,
  rhMin: 0,
  rhMax: 100,
} as const;

/** Pressão de saturação do vapor (kPa) na temperatura dada, pela equação de Tetens. */
export function saturationVaporPressure(tempC: number): number {
  return 0.61078 * Math.exp((17.27 * tempC) / (tempC + 237.3));
}

export function calculateVpd(input: VpdInput): VpdResult {
  assertInRange(input.airTempC, LIMITS.tempMin, LIMITS.tempMax, 'Temperatura do ar');
  assertInRange(input.relativeHumidity, LIMITS.rhMin, LIMITS.rhMax, 'Umidade relativa');
  const leafTempC = input.leafTempC ?? input.airTempC - DEFAULT_LEAF_OFFSET_C;
  assertInRange(leafTempC, LIMITS.tempMin, LIMITS.tempMax, 'Temperatura da folha');

  const airSvp = saturationVaporPressure(input.airTempC);
  const actualVaporPressure = airSvp * (input.relativeHumidity / 100);

  return {
    leafVpd: round2(Math.max(saturationVaporPressure(leafTempC) - actualVaporPressure, 0)),
    airVpd: round2(Math.max(airSvp - actualVaporPressure, 0)),
    leafTempC,
  };
}

export function classify(vpd: number, range: VpdRange): VpdZone {
  if (vpd < range.min) {
    return 'low';
  }
  return vpd > range.max ? 'high' : 'ideal';
}

/**
 * Faixa de umidade que coloca o VPD da folha dentro do alvo, mantida a temperatura atual.
 * É a resposta prática para "quanto devo ajustar o umidificador/desumidificador?".
 */
export function humidityRangeFor(range: VpdRange, airTempC: number, leafTempC: number): VpdRange {
  const airSvp = saturationVaporPressure(airTempC);
  const leafSvp = saturationVaporPressure(leafTempC);
  const rhFor = (vpd: number) => clamp(((leafSvp - vpd) / airSvp) * 100, 0, 100);
  // VPD maior exige umidade menor, por isso os limites se invertem.
  return { min: Math.round(rhFor(range.max)), max: Math.round(rhFor(range.min)) };
}

function assertInRange(value: number, min: number, max: number, field: string): void {
  if (!Number.isFinite(value) || value < min || value > max) {
    throw new RangeError(`${field} deve estar entre ${min} e ${max}`);
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
