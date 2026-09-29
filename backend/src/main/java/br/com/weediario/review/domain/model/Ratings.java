package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Notas de 1 a 5 estrelas por critério.
 */
public record Ratings(int vigor, int pestResistance, int easeOfGrowth, int yield, int terpeneProfile) {

    public static final int MIN = 1;
    public static final int MAX = 5;

    public Ratings {
        check(vigor, "Vigor vegetativo");
        check(pestResistance, "Resistência a pragas/estresse");
        check(easeOfGrowth, "Facilidade de cultivo");
        check(yield, "Rendimento");
        check(terpeneProfile, "Aroma/sabor");
    }

    public BigDecimal average() {
        int sum = vigor + pestResistance + easeOfGrowth + yield + terpeneProfile;
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(5), 1, RoundingMode.HALF_UP);
    }

    private static void check(int value, String criterion) {
        if (value < MIN || value > MAX) {
            throw new DomainException(criterion + " deve ter nota de " + MIN + " a " + MAX);
        }
    }
}
