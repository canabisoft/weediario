package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.GrowEnvironment;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SetupSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grow_setups")
public class GrowSetupJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = GrowSetup.NAME_MAX_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrowEnvironment environment;

    @Column(name = "tent_width_cm")
    private Integer tentWidthCm;

    @Column(name = "tent_depth_cm")
    private Integer tentDepthCm;

    @Column(name = "tent_height_cm")
    private Integer tentHeightCm;

    @Column(length = SetupSpec.TEXT_MAX_LENGTH)
    private String lighting;

    @Column(name = "light_wattage")
    private Integer lightWattage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GrowMedium medium;

    @Column(length = SetupSpec.TEXT_MAX_LENGTH)
    private String nutrients;

    @Column(length = SetupSpec.TEXT_MAX_LENGTH)
    private String ventilation;

    @Column(length = SetupSpec.NOTES_MAX_LENGTH)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrowSetupJpaEntity() {
    }

    static GrowSetupJpaEntity fromDomain(GrowSetup setup) {
        GrowSetupJpaEntity entity = new GrowSetupJpaEntity();
        SetupSpec spec = setup.spec();
        entity.id = setup.id().value();
        entity.ownerId = setup.ownerId().value();
        entity.name = setup.name();
        entity.environment = setup.environment();
        entity.tentWidthCm = spec.tentWidthCm();
        entity.tentDepthCm = spec.tentDepthCm();
        entity.tentHeightCm = spec.tentHeightCm();
        entity.lighting = spec.lighting();
        entity.lightWattage = spec.lightWattage();
        entity.medium = spec.medium();
        entity.nutrients = spec.nutrients();
        entity.ventilation = spec.ventilation();
        entity.notes = spec.notes();
        entity.createdAt = setup.createdAt();
        entity.updatedAt = setup.updatedAt();
        return entity;
    }

    GrowSetup toDomain() {
        SetupSpec spec = new SetupSpec(tentWidthCm, tentDepthCm, tentHeightCm, lighting, lightWattage, medium,
                nutrients, ventilation, notes);
        return GrowSetup.restore(new GrowSetupId(id), new GrowerId(ownerId), name, environment, spec,
                createdAt, updatedAt);
    }
}
