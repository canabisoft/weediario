import {
  ConcentrationUnit,
  DoseUnit,
  Intensity,
  Medicine,
  MedicineForm,
  Origin,
  Route,
  SideEffect,
  StockUnit,
} from '../core/api/treatment.api';
import { formatCompact } from '../shared/decimal';

/**
 * Textos do acompanhamento de tratamento. Regra do módulo: só descrever o que foi registrado. Nada de
 * "recomendado", "ideal", "sugerido" ou comparação de eficácia: isso é papel do profissional de saúde.
 */

export const TERMS_VERSION = '2026-09';

export const MEDICAL_NOTICE =
  'O weediário só registra o que você usou e como se sentiu. Não indica dose, horário nem produto, e não substitui ' +
  'a orientação do profissional que acompanha o tratamento.';

export const FORM_LABEL: Record<MedicineForm, string> = {
  OIL: 'Óleo',
  TINCTURE: 'Tintura',
  FLOWER: 'Flor',
  GUMMY: 'Goma',
  CAPSULE: 'Cápsula',
  TOPICAL: 'Pomada / tópico',
  EXTRACT: 'Extrato / rosin',
};

export const ORIGIN_LABEL: Record<Origin, string> = {
  HOME_GROWN: 'Cultivo próprio',
  ASSOCIATION: 'Associação',
  PHARMACY: 'Farmácia',
  IMPORTED: 'Importado',
  OTHER: 'Outra',
};

export const ROUTE_LABEL: Record<Route, string> = {
  SUBLINGUAL: 'Sublingual',
  ORAL: 'Oral',
  INHALED: 'Inalada / vaporizada',
  TOPICAL: 'Na pele',
  OTHER: 'Outra',
};

export const DOSE_UNIT_LABEL: Record<DoseUnit, string> = {
  DROP: 'gotas',
  ML: 'ml',
  G: 'g',
  UNIT: 'unidades',
  APPLICATION: 'aplicação',
};

export const STOCK_UNIT_LABEL: Record<StockUnit, string> = { ML: 'ml', G: 'g', UNIT: 'un.' };

export const CONCENTRATION_LABEL: Record<ConcentrationUnit, string> = {
  MG_PER_ML: 'mg/ml',
  PERCENT: '%',
  MG_PER_UNIT: 'mg por unidade',
  MG_PER_G: 'mg/g',
};

export const SIDE_EFFECT_LABEL: Record<SideEffect, string> = {
  DROWSINESS: 'Sonolência',
  DIZZINESS: 'Tontura',
  DRY_MOUTH: 'Boca seca',
  RED_EYES: 'Olhos vermelhos',
  HUNGER: 'Fome',
  ANXIETY: 'Ansiedade',
  TACHYCARDIA: 'Coração acelerado',
  NAUSEA: 'Náusea',
  HEADACHE: 'Dor de cabeça',
  OTHER: 'Outro',
};

export const INTENSITY_LABEL: Record<Intensity, string> = { MILD: 'Leve', MODERATE: 'Moderado', SEVERE: 'Forte' };

/** Unidades da forma, como o backend: estoque, concentração e unidades aceitas no registro. */
export const FORM_UNITS: Record<MedicineForm, { stock: StockUnit; concentration: ConcentrationUnit; dose: DoseUnit[] }> = {
  OIL: { stock: 'ML', concentration: 'MG_PER_ML', dose: ['DROP', 'ML'] },
  TINCTURE: { stock: 'ML', concentration: 'MG_PER_ML', dose: ['DROP', 'ML'] },
  FLOWER: { stock: 'G', concentration: 'PERCENT', dose: ['G'] },
  GUMMY: { stock: 'UNIT', concentration: 'MG_PER_UNIT', dose: ['UNIT'] },
  CAPSULE: { stock: 'UNIT', concentration: 'MG_PER_UNIT', dose: ['UNIT'] },
  TOPICAL: { stock: 'G', concentration: 'MG_PER_G', dose: ['G', 'APPLICATION'] },
  EXTRACT: { stock: 'G', concentration: 'PERCENT', dose: ['G'] },
};

export const USUAL_ROUTE: Record<MedicineForm, Route> = {
  OIL: 'SUBLINGUAL',
  TINCTURE: 'SUBLINGUAL',
  FLOWER: 'INHALED',
  GUMMY: 'ORAL',
  CAPSULE: 'ORAL',
  TOPICAL: 'TOPICAL',
  EXTRACT: 'INHALED',
};

/**
 * Prévia dos mg que serão registrados, com a mesma conta do backend (que é quem grava o valor).
 * É aritmética sobre o rótulo, não orientação de uso.
 */
export function previewMg(medicine: Medicine, amount: number, unit: DoseUnit): { thc?: number; cbd?: number } {
  if (!(amount > 0) || unit === 'APPLICATION') {
    return {};
  }
  const stock = unit === 'DROP' ? amount / (medicine.dropsPerMl ?? 20) : amount;
  const perUnit = (value?: number) =>
    value == null ? undefined : medicine.concentrationUnit === 'PERCENT' ? value * 10 : value;
  const mg = (value?: number) => {
    const per = perUnit(value);
    return per == null ? undefined : Math.round(stock * per * 1000) / 1000;
  };
  return { thc: mg(medicine.thc), cbd: mg(medicine.cbd) };
}

export function mg(value: number | undefined | null): string {
  return value == null ? '—' : `${formatCompact(value, 1)} mg`;
}

export function quantity(value: number, unit: StockUnit): string {
  return `${formatCompact(value, 2)} ${STOCK_UNIT_LABEL[unit]}`;
}

export function concentration(medicine: Pick<Medicine, 'thc' | 'cbd' | 'concentrationUnit'>): string {
  if (medicine.thc == null && medicine.cbd == null) {
    return 'Concentração não informada';
  }
  const unit = CONCENTRATION_LABEL[medicine.concentrationUnit];
  const part = (label: string, value?: number) => (value == null ? null : `${label} ${formatCompact(value, 3)} ${unit}`);
  return [part('THC', medicine.thc), part('CBD', medicine.cbd)].filter(Boolean).join(' · ');
}

/** Data local "yyyy-MM-dd" a partir de um Date. */
export function isoDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const dayAndTime = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });

export function clock(instant: string): string {
  return time.format(new Date(instant));
}

export function when(instant: string): string {
  return dayAndTime.format(new Date(instant));
}

/** Baixa um arquivo vindo da API (PDF, JSON) sem abrir nova aba. */
export function saveFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
