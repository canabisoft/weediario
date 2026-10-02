package br.com.weediario.catalog.domain.repository;

import br.com.weediario.catalog.domain.repository.CatalogRepository.ItemKind;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro das ações da moderação no catálogo, para nenhuma correção, junção ou exclusão acontecer sem rastro.
 * Só inserção; apagado depois de {@link #RETENTION_YEARS} anos.
 */
public interface CatalogAuditLog {

    int RETENTION_YEARS = 5;

    void record(Entry entry);

    void deleteBefore(Instant cutoff);

    enum Action {
        CORRECTED,
        VERIFIED,
        MERGED,
        REJECTED,
        REPRESENTATIVE_APPROVED,
        REPRESENTATIVE_REVOKED
    }

    /**
     * @param itemName  nome na data da ação (o item pode deixar de existir)
     * @param relatedId item principal numa junção, ou a conta do representante
     */
    record Entry(Action action, ItemKind kind, UUID itemId, String itemName, UUID relatedId, UUID moderatorId,
                 Instant at) {
    }
}
