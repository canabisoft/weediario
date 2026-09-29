package br.com.weediario.diary.domain.repository;

import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.model.GrowerId;

import java.util.List;
import java.util.Optional;

public interface GrowSetupRepository {

    GrowSetup save(GrowSetup setup);

    Optional<GrowSetup> findById(GrowSetupId id);

    List<GrowSetup> findByOwner(GrowerId ownerId);

    long countByOwner(GrowerId ownerId);

    void delete(GrowSetupId id);
}
