package br.com.weediario.diary.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataGrowSetupRepository extends JpaRepository<GrowSetupJpaEntity, UUID> {

    List<GrowSetupJpaEntity> findByOwnerIdOrderByNameAsc(UUID ownerId);

    long countByOwnerId(UUID ownerId);
}
