package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowthStage;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Strain;
import br.com.weediario.diary.domain.model.Visibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "grow_diaries")
public class GrowDiaryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = GrowDiary.TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "strain_name", nullable = false, length = Strain.NAME_MAX_LENGTH)
    private String strainName;

    @Column(length = Strain.BREEDER_MAX_LENGTH)
    private String breeder;

    @Enumerated(EnumType.STRING)
    @Column(name = "flowering_type", nullable = false, length = 20)
    private FloweringType floweringType;

    @Enumerated(EnumType.STRING)
    @Column(name = "seed_sex", nullable = false, length = 20)
    private SeedSex seedSex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrowMedium medium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiaryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 20)
    private GrowthStage currentStage;

    @Column(name = "last_update_on")
    private LocalDate lastUpdateOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrowDiaryJpaEntity() {
    }

    static GrowDiaryJpaEntity fromDomain(GrowDiary diary) {
        GrowDiaryJpaEntity entity = new GrowDiaryJpaEntity();
        entity.id = diary.id().value();
        entity.ownerId = diary.ownerId().value();
        entity.title = diary.title();
        entity.strainName = diary.strain().name();
        entity.breeder = diary.strain().breeder();
        entity.floweringType = diary.floweringType();
        entity.seedSex = diary.seedSex();
        entity.medium = diary.medium();
        entity.status = diary.status();
        entity.visibility = diary.visibility();
        entity.startedOn = diary.startedOn();
        entity.endedOn = diary.endedOn();
        entity.currentStage = diary.currentStage();
        entity.lastUpdateOn = diary.lastUpdateOn();
        entity.createdAt = diary.createdAt();
        entity.updatedAt = diary.updatedAt();
        return entity;
    }

    GrowDiary toDomain() {
        return GrowDiary.restore(new GrowDiaryId(id), new GrowerId(ownerId), title, new Strain(strainName, breeder),
                floweringType, seedSex, medium, status, visibility, startedOn, endedOn, currentStage, lastUpdateOn,
                createdAt, updatedAt);
    }
}
