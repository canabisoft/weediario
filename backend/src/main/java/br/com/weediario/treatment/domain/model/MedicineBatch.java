package br.com.weediario.treatment.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.ConcentrationUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.DoseUnit;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.Origin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lote de um remédio pronto (óleo, flor, pomada...) do paciente. A concentração é a informada no rótulo ou laudo;
 * o app só faz a conta do que foi usado, nunca sugere quanto usar.
 * <p>
 * O saldo não é guardado: é a quantidade inicial menos as doses mais os ajustes, então apagar uma dose
 * registrada por engano devolve a quantidade sozinho.
 *
 * @param thc         concentração de THC na unidade da forma ({@link MedicineForm#concentrationUnit()}), se conhecida
 * @param cbd         concentração de CBD, se conhecida
 * @param dropsPerMl  calibração do conta-gotas (só óleo e tintura)
 */
public record MedicineBatch(UUID id, UUID patientId, String name, MedicineForm form, Origin origin, BigDecimal thc,
                            BigDecimal cbd, BigDecimal dropsPerMl, BigDecimal initialQuantity, LocalDate manufacturedOn,
                            LocalDate expiresOn, String notes, boolean archived, Instant createdAt) {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int NOTES_MAX_LENGTH = 1000;
    public static final BigDecimal DEFAULT_DROPS_PER_ML = BigDecimal.valueOf(20);
    private static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(100_000);
    private static final int SCALE = 3;

    public MedicineBatch {
        Guard.notNull(id, "Id");
        Guard.notNull(patientId, "Paciente");
        name = Guard.lengthBetween(Guard.notBlank(name, "Nome").strip(), 1, NAME_MAX_LENGTH, "Nome");
        Guard.notNull(form, "Forma farmacêutica");
        Guard.notNull(origin, "Origem");
        thc = concentration(thc, form, "THC");
        cbd = concentration(cbd, form, "CBD");
        if (form.doseUnits().contains(DoseUnit.DROP)) {
            dropsPerMl = dropsPerMl == null ? DEFAULT_DROPS_PER_ML : dropsPerMl;
            if (dropsPerMl.compareTo(BigDecimal.valueOf(5)) < 0 || dropsPerMl.compareTo(BigDecimal.valueOf(60)) > 0) {
                throw new DomainException("Gotas por ml deve ficar entre 5 e 60");
            }
        } else {
            dropsPerMl = null;
        }
        Guard.notNull(initialQuantity, "Quantidade inicial");
        if (initialQuantity.signum() <= 0 || initialQuantity.compareTo(MAX_QUANTITY) > 0) {
            throw new DomainException("Quantidade inicial inválida");
        }
        if (manufacturedOn != null && expiresOn != null && expiresOn.isBefore(manufacturedOn)) {
            throw new DomainException("A validade não pode ser antes da fabricação");
        }
        notes = optionalText(notes, NOTES_MAX_LENGTH, "Observações");
        Guard.notNull(createdAt, "Data de criação");
    }

    public static MedicineBatch register(UUID patientId, String name, MedicineForm form, Origin origin, BigDecimal thc,
                                         BigDecimal cbd, BigDecimal dropsPerMl, BigDecimal initialQuantity,
                                         LocalDate manufacturedOn, LocalDate expiresOn, String notes, Clock clock) {
        return new MedicineBatch(UUID.randomUUID(), patientId, name, form, origin, thc, cbd, dropsPerMl, initialQuantity,
                manufacturedOn, expiresOn, notes, false, clock.instant());
    }

    /** Edita os dados do rótulo. A quantidade inicial não muda: correções de estoque viram ajustes. */
    public MedicineBatch withDetails(String name, Origin origin, BigDecimal thc, BigDecimal cbd, BigDecimal dropsPerMl,
                                     LocalDate manufacturedOn, LocalDate expiresOn, String notes) {
        return new MedicineBatch(id, patientId, name, form, origin, thc, cbd, dropsPerMl, initialQuantity, manufacturedOn,
                expiresOn, notes, archived, createdAt);
    }

    public MedicineBatch archive(boolean value) {
        return new MedicineBatch(id, patientId, name, form, origin, thc, cbd, dropsPerMl, initialQuantity, manufacturedOn,
                expiresOn, notes, value, createdAt);
    }

    public boolean expiredOn(LocalDate day) {
        return expiresOn != null && expiresOn.isBefore(day);
    }

    /**
     * Converte o que o paciente informou na quantidade que sai do estoque e nos mg de THC/CBD correspondentes,
     * a partir da concentração do rótulo. Para flor e extrato é o teor total no material (não o absorvido).
     */
    public DoseMeasure measure(BigDecimal amount, DoseUnit unit) {
        Guard.notNull(amount, "Quantidade");
        Guard.notNull(unit, "Unidade");
        if (amount.signum() <= 0 || amount.compareTo(MAX_QUANTITY) > 0) {
            throw new DomainException("Quantidade inválida");
        }
        if (!form.doseUnits().contains(unit)) {
            throw new DomainException("Unidade não combina com a forma do remédio");
        }
        BigDecimal stock = switch (unit) {
            case DROP -> amount.divide(dropsPerMl, SCALE, RoundingMode.HALF_UP);
            case ML, G, UNIT -> amount.setScale(SCALE, RoundingMode.HALF_UP);
            case APPLICATION -> null;
        };
        return new DoseMeasure(stock, milligrams(stock, thc), milligrams(stock, cbd));
    }

    private BigDecimal milligrams(BigDecimal stockQuantity, BigDecimal concentration) {
        if (stockQuantity == null || concentration == null) {
            return null;
        }
        BigDecimal perStockUnit = form.concentrationUnit() == ConcentrationUnit.PERCENT
                ? concentration.multiply(BigDecimal.TEN) // % em g: 1 g a 20% = 200 mg
                : concentration;
        return stockQuantity.multiply(perStockUnit).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal concentration(BigDecimal value, MedicineForm form, String label) {
        if (value == null) {
            return null;
        }
        BigDecimal max = form.concentrationUnit() == ConcentrationUnit.PERCENT ? BigDecimal.valueOf(100) : BigDecimal.valueOf(1000);
        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw new DomainException("Concentração de " + label + " fora do intervalo");
        }
        return value;
    }

    static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Guard.lengthBetween(value.strip(), 1, max, field);
    }

    /**
     * @param stockQuantity quanto sai do estoque, na unidade do estoque (nulo quando não dá para saber)
     * @param thcMg         mg de THC calculados (nulo sem concentração informada)
     */
    public record DoseMeasure(BigDecimal stockQuantity, BigDecimal thcMg, BigDecimal cbdMg) {
    }
}
