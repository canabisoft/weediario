package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.repository.GrowSetupRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaGrowSetupRepository implements GrowSetupRepository {

    private final SpringDataGrowSetupRepository jpa;

    public JpaGrowSetupRepository(SpringDataGrowSetupRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public GrowSetup save(GrowSetup setup) {
        return jpa.save(GrowSetupJpaEntity.fromDomain(setup)).toDomain();
    }

    @Override
    public Optional<GrowSetup> findById(GrowSetupId id) {
        return jpa.findById(id.value()).map(GrowSetupJpaEntity::toDomain);
    }

    @Override
    public List<GrowSetup> findByOwner(GrowerId ownerId) {
        return jpa.findByOwnerIdOrderByNameAsc(ownerId.value()).stream().map(GrowSetupJpaEntity::toDomain).toList();
    }

    @Override
    public long countByOwner(GrowerId ownerId) {
        return jpa.countByOwnerId(ownerId.value());
    }

    @Override
    public void delete(GrowSetupId id) {
        jpa.deleteById(id.value());
    }
}
