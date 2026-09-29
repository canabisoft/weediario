package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Notas de 1 a 5 para o banco de sementes (seedbank/breeder) da genética cultivada.
 */
public record SeedbankRatings(int germination, int stability, int service) {

    public SeedbankRatings {
        check(germination, "Germinação");
        check(stability, "Estabilidade genética");
        check(service, "Atendimento e entrega");
    }

    public BigDecimal average() {
        return BigDecimal.valueOf(germination + stability + service).divide(BigDecimal.valueOf(3), 1, RoundingMode.HALF_UP);
    }

    private static void check(int value, String criterion) {
        if (value < Ratings.MIN || value > Ratings.MAX) {
            throw new DomainException(criterion + " deve ter nota de " + Ratings.MIN + " a " + Ratings.MAX);
        }
    }
}
