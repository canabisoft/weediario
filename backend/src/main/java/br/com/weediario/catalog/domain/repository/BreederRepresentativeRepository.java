package br.com.weediario.catalog.domain.repository;

import br.com.weediario.catalog.domain.model.BreederRepresentative;

import java.util.Optional;
import java.util.UUID;

/**
 * Pedidos e vínculos de representantes de breeders. Recusar ou revogar apaga o registro.
 */
public interface BreederRepresentativeRepository {

    Optional<BreederRepresentative> find(UUID breederId, UUID userId);

    BreederRepresentative save(BreederRepresentative representative);

    /** @return {@code false} se não havia pedido */
    boolean delete(UUID breederId, UUID userId);

    long countPendingBy(UUID userId);
}
