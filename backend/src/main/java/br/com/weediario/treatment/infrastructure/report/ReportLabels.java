package br.com.weediario.treatment.infrastructure.report;

import br.com.weediario.treatment.domain.model.MedicineForm;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.ConcentrationUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Route;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SideEffect;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.StockUnit;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/** Textos em português do relatório. */
final class ReportLabels {

    static final Locale PT_BR = Locale.of("pt", "BR");

    static final Map<MedicineForm, String> FORM = Map.of(
            MedicineForm.OIL, "Óleo", MedicineForm.TINCTURE, "Tintura", MedicineForm.FLOWER, "Flor",
            MedicineForm.GUMMY, "Goma", MedicineForm.CAPSULE, "Cápsula", MedicineForm.TOPICAL, "Pomada/tópico",
            MedicineForm.EXTRACT, "Extrato/rosin");

    static final Map<Origin, String> ORIGIN = Map.of(
            Origin.HOME_GROWN, "Cultivo próprio", Origin.ASSOCIATION, "Associação", Origin.PHARMACY, "Farmácia",
            Origin.IMPORTED, "Importado", Origin.OTHER, "Outra");

    static final Map<Route, String> ROUTE = Map.of(
            Route.SUBLINGUAL, "Sublingual", Route.ORAL, "Oral", Route.INHALED, "Inalada", Route.TOPICAL, "Tópica",
            Route.OTHER, "Outra");

    static final Map<DoseUnit, String> DOSE_UNIT = Map.of(
            DoseUnit.DROP, "gotas", DoseUnit.ML, "ml", DoseUnit.G, "g", DoseUnit.UNIT, "un.",
            DoseUnit.APPLICATION, "aplicação");

    static final Map<StockUnit, String> STOCK_UNIT = Map.of(StockUnit.ML, "ml", StockUnit.G, "g", StockUnit.UNIT, "un.");

    static final Map<ConcentrationUnit, String> CONCENTRATION = Map.of(
            ConcentrationUnit.MG_PER_ML, "mg/ml", ConcentrationUnit.PERCENT, "%",
            ConcentrationUnit.MG_PER_UNIT, "mg/un.", ConcentrationUnit.MG_PER_G, "mg/g");

    static final Map<SideEffect, String> SIDE_EFFECT = Map.of(
            SideEffect.DIZZINESS, "Tontura", SideEffect.DROWSINESS, "Sonolência", SideEffect.DRY_MOUTH, "Boca seca",
            SideEffect.RED_EYES, "Olhos vermelhos", SideEffect.TACHYCARDIA, "Taquicardia",
            SideEffect.ANXIETY, "Ansiedade", SideEffect.HUNGER, "Fome", SideEffect.NAUSEA, "Náusea",
            SideEffect.HEADACHE, "Dor de cabeça", SideEffect.OTHER, "Outro");

    private ReportLabels() {
    }

    static String number(BigDecimal value, int maxDecimals) {
        if (value == null) {
            return "—";
        }
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMaximumFractionDigits(maxDecimals);
        format.setMinimumFractionDigits(0);
        return format.format(value);
    }
}
