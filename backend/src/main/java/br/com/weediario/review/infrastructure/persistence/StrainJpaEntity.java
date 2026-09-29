package br.com.weediario.review.infrastructure.persistence;

import br.com.weediario.review.domain.model.Strain;
import br.com.weediario.review.domain.model.StrainId;
import br.com.weediario.review.domain.model.StrainKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "strains")
public class StrainJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = Strain.NAME_MAX_LENGTH)
    private String name;

    @Column(length = 80)
    private String breeder;

    @Column(name = "name_key", nullable = false, length = Strain.NAME_MAX_LENGTH)
    private String nameKey;

    @Column(name = "breeder_key", nullable = false, length = 80)
    private String breederKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StrainJpaEntity() {
    }

    static StrainJpaEntity fromDomain(Strain strain) {
        StrainJpaEntity entity = new StrainJpaEntity();
        entity.id = strain.id().value();
        entity.name = strain.name();
        entity.breeder = strain.breeder();
        entity.nameKey = strain.key().name();
        entity.breederKey = strain.key().breeder();
        entity.createdAt = strain.createdAt();
        return entity;
    }

    Strain toDomain() {
        return Strain.restore(new StrainId(id), name, breeder, new StrainKey(nameKey, breederKey), createdAt);
    }
}
