package br.com.weediario.review.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataStrainRepository extends JpaRepository<StrainJpaEntity, UUID> {

    Optional<StrainJpaEntity> findByNameKeyAndBreederKey(String nameKey, String breederKey);
}
