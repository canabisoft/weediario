package br.com.weediario.diary.infrastructure.persistence;

import br.com.weediario.diary.domain.model.DiaryUpdate;
import br.com.weediario.diary.domain.model.DiaryUpdateId;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowMetrics;
import br.com.weediario.diary.domain.model.GrowthStage;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "diary_updates")
public class DiaryUpdateJpaEntity {

    @Id
    private UUID id;

    @Column(name = "diary_id", nullable = false, updatable = false)
    private UUID diaryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrowthStage stage;

    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    @Column(length = DiaryUpdate.NOTE_MAX_LENGTH)
    private String note;

    @Column(name = "temperature_celsius", precision = 4, scale = 1)
    private BigDecimal temperatureCelsius;

    @Column(name = "relative_humidity", precision = 4, scale = 1)
    private BigDecimal relativeHumidity;

    @Column(name = "watering_ph", precision = 4, scale = 2)
    private BigDecimal wateringPh;

    @Column(name = "plant_height_cm", precision = 5, scale = 1)
    private BigDecimal plantHeightCm;

    @ElementCollection
    @CollectionTable(name = "diary_update_photos", joinColumns = @JoinColumn(name = "update_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_id", nullable = false)
    @BatchSize(size = 50)
    private List<UUID> photoIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiaryUpdateJpaEntity() {
    }

    static DiaryUpdateJpaEntity fromDomain(DiaryUpdate update) {
        DiaryUpdateJpaEntity entity = new DiaryUpdateJpaEntity();
        entity.id = update.id().value();
        entity.diaryId = update.diaryId().value();
        entity.stage = update.stage();
        entity.recordedOn = update.recordedOn();
        entity.note = update.note();
        entity.temperatureCelsius = update.metrics().temperatureCelsius();
        entity.relativeHumidity = update.metrics().relativeHumidity();
        entity.wateringPh = update.metrics().wateringPh();
        entity.plantHeightCm = update.metrics().plantHeightCm();
        entity.photoIds = new ArrayList<>(update.photoIds());
        entity.createdAt = update.createdAt();
        return entity;
    }

    DiaryUpdate toDomain() {
        return DiaryUpdate.restore(new DiaryUpdateId(id), new GrowDiaryId(diaryId), stage, recordedOn, note,
                new GrowMetrics(temperatureCelsius, relativeHumidity, wateringPh, plantHeightCm),
                List.copyOf(photoIds), createdAt);
    }
}
