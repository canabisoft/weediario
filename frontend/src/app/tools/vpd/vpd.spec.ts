import { VPD_STAGES, calculateVpd, classify, humidityRangeFor, saturationVaporPressure } from './vpd';

describe('VPD', () => {
  const vegetative = VPD_STAGES.find((stage) => stage.id === 'vegetative')!.range;

  it('computes saturation vapor pressure with Tetens', () => {
    expect(saturationVaporPressure(0)).toBeCloseTo(0.611, 3);
    expect(saturationVaporPressure(25)).toBeCloseTo(3.168, 2);
  });

  it('computes leaf and air VPD with the default 2 °C leaf offset', () => {
    const result = calculateVpd({ airTempC: 25, relativeHumidity: 60 });

    expect(result.leafTempC).toBe(23);
    expect(result.leafVpd).toBeCloseTo(0.91, 2);
    expect(result.airVpd).toBeCloseTo(1.27, 2);
  });

  it('uses the measured leaf temperature when given', () => {
    const sameAsAir = calculateVpd({ airTempC: 25, relativeHumidity: 60, leafTempC: 25 });

    expect(sameAsAir.leafVpd).toBe(sameAsAir.airVpd);
  });

  it('never returns negative VPD (condensation)', () => {
    expect(calculateVpd({ airTempC: 20, relativeHumidity: 100, leafTempC: 15 }).leafVpd).toBe(0);
  });

  it('classifies against the stage range', () => {
    expect(classify(0.7, vegetative)).toBe('low');
    expect(classify(1.0, vegetative)).toBe('ideal');
    expect(classify(1.3, vegetative)).toBe('high');
  });

  it('suggests a humidity range that lands inside the ideal VPD', () => {
    const target = humidityRangeFor(vegetative, 25, 23);

    expect(target).toEqual({ min: 51, max: 63 });
    const middle = (target.min + target.max) / 2;
    expect(classify(calculateVpd({ airTempC: 25, relativeHumidity: middle }).leafVpd, vegetative)).toBe('ideal');
  });

  it('rejects impossible readings', () => {
    expect(() => calculateVpd({ airTempC: 80, relativeHumidity: 50 })).toThrowError(RangeError);
    expect(() => calculateVpd({ airTempC: 25, relativeHumidity: 120 })).toThrowError(/Umidade/);
    expect(() => calculateVpd({ airTempC: Number.NaN, relativeHumidity: 50 })).toThrowError(RangeError);
  });
});
