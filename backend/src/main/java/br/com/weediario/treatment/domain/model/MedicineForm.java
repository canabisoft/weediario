package br.com.weediario.treatment.domain.model;

import br.com.weediario.treatment.domain.model.TreatmentVocabulary.ConcentrationUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockUnit;

import java.util.Set;

/**
 * Forma farmacêutica: define em que unidade o estoque é contado, como a concentração é informada e em quais
 * unidades o uso pode ser registrado.
 */
public enum MedicineForm {

    OIL(StockUnit.ML, ConcentrationUnit.MG_PER_ML, Set.of(DoseUnit.DROP, DoseUnit.ML), Route.SUBLINGUAL),
    TINCTURE(StockUnit.ML, ConcentrationUnit.MG_PER_ML, Set.of(DoseUnit.DROP, DoseUnit.ML), Route.SUBLINGUAL),
    FLOWER(StockUnit.G, ConcentrationUnit.PERCENT, Set.of(DoseUnit.G), Route.INHALED),
    GUMMY(StockUnit.UNIT, ConcentrationUnit.MG_PER_UNIT, Set.of(DoseUnit.UNIT), Route.ORAL),
    CAPSULE(StockUnit.UNIT, ConcentrationUnit.MG_PER_UNIT, Set.of(DoseUnit.UNIT), Route.ORAL),
    TOPICAL(StockUnit.G, ConcentrationUnit.MG_PER_G, Set.of(DoseUnit.G, DoseUnit.APPLICATION), Route.TOPICAL),
    EXTRACT(StockUnit.G, ConcentrationUnit.PERCENT, Set.of(DoseUnit.G), Route.INHALED);

    private final StockUnit stockUnit;
    private final ConcentrationUnit concentrationUnit;
    private final Set<DoseUnit> doseUnits;
    private final Route usualRoute;

    MedicineForm(StockUnit stockUnit, ConcentrationUnit concentrationUnit, Set<DoseUnit> doseUnits, Route usualRoute) {
        this.stockUnit = stockUnit;
        this.concentrationUnit = concentrationUnit;
        this.doseUnits = doseUnits;
        this.usualRoute = usualRoute;
    }

    public StockUnit stockUnit() {
        return stockUnit;
    }

    public ConcentrationUnit concentrationUnit() {
        return concentrationUnit;
    }

    public Set<DoseUnit> doseUnits() {
        return doseUnits;
    }

    /** Via mais comum da forma, só para pré-preencher o registro (o paciente informa a via real). */
    public Route usualRoute() {
        return usualRoute;
    }
}
