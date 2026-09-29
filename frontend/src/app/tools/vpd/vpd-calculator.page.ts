import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatDecimal, parseDecimal } from '../../shared/decimal';
import {
  DEFAULT_LEAF_OFFSET_C,
  VPD_STAGES,
  VpdResult,
  VpdStage,
  VpdZone,
  calculateVpd,
  classify,
  humidityRangeFor,
} from './vpd';

type LeafMode = 'offset' | 'measured';

interface Outcome {
  readonly result: VpdResult;
  readonly zone: VpdZone;
  readonly humidityTarget: { min: number; max: number };
}

const CHART_TEMPS = Array.from({ length: 15 }, (_, i) => 18 + i);
const CHART_HUMIDITIES = Array.from({ length: 12 }, (_, i) => 30 + i * 5);

@Component({
  selector: 'app-vpd-calculator-page',
  imports: [RouterLink],
  templateUrl: './vpd-calculator.page.html',
})
export class VpdCalculatorPage {
  protected readonly stages = VPD_STAGES;
  protected readonly chartTemps = CHART_TEMPS;
  protected readonly chartHumidities = CHART_HUMIDITIES;
  protected readonly defaultOffset = DEFAULT_LEAF_OFFSET_C;

  protected readonly airTemp = signal('25');
  protected readonly humidity = signal('60');
  protected readonly leafMode = signal<LeafMode>('offset');
  protected readonly leafOffset = signal(String(DEFAULT_LEAF_OFFSET_C));
  protected readonly leafTemp = signal('23');
  protected readonly stage = signal<VpdStage>('vegetative');

  protected readonly stageInfo = computed(() => VPD_STAGES.find((s) => s.id === this.stage())!);

  private readonly inputs = computed(() => {
    const air = parseDecimal(this.airTemp());
    const rh = parseDecimal(this.humidity());
    const leaf =
      this.leafMode() === 'measured'
        ? parseDecimal(this.leafTemp())
        : air === null
          ? null
          : air - (parseDecimal(this.leafOffset()) ?? DEFAULT_LEAF_OFFSET_C);
    return { air, rh, leaf };
  });

  private readonly evaluation = computed<Outcome | string>(() => {
    const { air, rh, leaf } = this.inputs();
    if (air === null || rh === null || leaf === null) {
      return 'Preencha temperatura e umidade.';
    }
    try {
      const result = calculateVpd({ airTempC: air, relativeHumidity: rh, leafTempC: leaf });
      const range = this.stageInfo().range;
      return {
        result,
        zone: classify(result.leafVpd, range),
        humidityTarget: humidityRangeFor(range, air, result.leafTempC),
      };
    } catch (error) {
      return error instanceof RangeError ? error.message : 'Valores inválidos.';
    }
  });

  protected readonly outcome = computed(() => {
    const value = this.evaluation();
    return typeof value === 'string' ? null : value;
  });

  protected readonly problem = computed(() => {
    const value = this.evaluation();
    return typeof value === 'string' ? value : null;
  });

  /** Tabela VPD: cada célula é o VPD da folha para aquela temperatura/umidade, com o deslocamento atual. */
  protected readonly chart = computed(() => {
    const offset = this.leafMode() === 'offset' ? (parseDecimal(this.leafOffset()) ?? DEFAULT_LEAF_OFFSET_C) : DEFAULT_LEAF_OFFSET_C;
    const range = this.stageInfo().range;
    return CHART_TEMPS.map((temp) =>
      CHART_HUMIDITIES.map((rh) => {
        const vpd = calculateVpd({ airTempC: temp, relativeHumidity: rh, leafTempC: temp - offset }).leafVpd;
        return { vpd, zone: classify(vpd, range) };
      }),
    );
  });

  protected readonly highlighted = computed(() => {
    const { air, rh } = this.inputs();
    if (air === null || rh === null) {
      return null;
    }
    return { temp: Math.round(air), rh: Math.round(rh / 5) * 5 };
  });

  protected fmt(value: number, decimals = 2): string {
    return formatDecimal(value, decimals);
  }

  protected zoneLabel(zone: VpdZone): string {
    return { low: 'Abaixo do ideal', ideal: 'Na faixa ideal', high: 'Acima do ideal' }[zone];
  }

  protected zoneAdvice(zone: VpdZone): string {
    return {
      low: 'Ar úmido demais para a temperatura: a planta transpira pouco e aumenta o risco de fungos e mofo. Reduza a umidade ou aumente a temperatura.',
      ideal: 'Transpiração e absorção de nutrientes equilibradas para esta fase.',
      high: 'Ar seco demais: a planta perde água rápido e pode fechar os estômatos. Aumente a umidade ou reduza a temperatura.',
    }[zone];
  }

  protected cellClass(zone: VpdZone, temp: number, rh: number): string {
    const zoneClass = { low: 'bg-warn/15', ideal: 'bg-ok/30 font-semibold', high: 'bg-danger/15' }[zone];
    const current = this.highlighted();
    const isCurrent = current?.temp === temp && current?.rh === rh;
    return isCurrent ? `${zoneClass} outline-2 -outline-offset-2 outline-fg` : zoneClass;
  }

  protected zoneIcon(zone: VpdZone): string {
    return { low: '▼', ideal: '✓', high: '▲' }[zone];
  }

  protected onStage(value: string): void {
    this.stage.set(value as VpdStage);
  }
}
