package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.Strain;
import br.com.weediario.review.domain.model.StrainKey;
import br.com.weediario.review.domain.repository.StrainRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaStrainRepository implements StrainRepository {

    private final SpringDataStrainRepository jpa;

    public JpaStrainRepository(SpringDataStrainRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Strain save(Strain strain) {
        return jpa.save(StrainJpaEntity.fromDomain(strain)).toDomain();
    }

    @Override
    public Optional<Strain> findByKey(StrainKey key) {
        return jpa.findByNameKeyAndBreederKey(key.name(), key.breeder()).map(StrainJpaEntity::toDomain);
    }
}
