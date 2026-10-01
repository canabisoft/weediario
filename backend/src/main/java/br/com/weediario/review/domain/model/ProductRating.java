package br.com.weediario.review.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.util.UUID;

/**
 * Nota de 1 a 5 para um produto (fertilizante, substrato...) usado no cultivo avaliado.
 */
public record ProductRating(UUID productId, int rating) {

    public ProductRating {
        Guard.notNull(productId, "Produto");
        if (rating < Ratings.MIN || rating > Ratings.MAX) {
            throw new DomainException("A nota do produto deve ser de " + Ratings.MIN + " a " + Ratings.MAX);
        }
    }
}
