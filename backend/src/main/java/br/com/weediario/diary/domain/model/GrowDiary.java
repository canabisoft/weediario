package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.domain.Guard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Aggregate root de um cultivo.
 * <p>
 * Privacidade por design: o diário não guarda localização (GPS, cidade, estado), IP nem dados do dispositivo.
 * Datas de cultivo têm granularidade de dia; horário exato só existe nos timestamps técnicos de auditoria.
 */
public final class GrowDiary {

    public static final int TITLE_MAX_LENGTH = 100;

    private final GrowDiaryId id;
    private final GrowerId ownerId;
    private String title;
    private final Strain strain;
    private final FloweringType floweringType;
    private final SeedSex seedSex;
    private final GrowMedium medium;
    private DiaryStatus status;
    private Visibility visibility;
    private final LocalDate startedOn;
    private LocalDate endedOn;
    private GrowthStage currentStage;
    private LocalDate lastUpdateOn;
    private final Instant createdAt;
    private Instant updatedAt;

    private GrowDiary(GrowDiaryId id, GrowerId ownerId, String title, Strain strain, FloweringType floweringType,
                      SeedSex seedSex, GrowMedium medium, DiaryStatus status, Visibility visibility,
                      LocalDate startedOn, LocalDate endedOn, GrowthStage currentStage, LocalDate lastUpdateOn,
                      Instant createdAt, Instant updatedAt) {
        this.id = Guard.notNull(id, "Id");
        this.ownerId = Guard.notNull(ownerId, "Autor");
        this.title = validTitle(title);
        this.strain = Guard.notNull(strain, "Strain");
        this.floweringType = Guard.notNull(floweringType, "Tipo de floração");
        this.seedSex = Guard.notNull(seedSex, "Tipo de semente");
        this.medium = Guard.notNull(medium, "Meio de cultivo");
        this.status = Guard.notNull(status, "Status");
        this.visibility = Guard.notNull(visibility, "Visibilidade");
        this.startedOn = Guard.notNull(startedOn, "Data de início");
        this.endedOn = endedOn;
        this.currentStage = Guard.notNull(currentStage, "Estágio atual");
        this.lastUpdateOn = lastUpdateOn;
        this.createdAt = Guard.notNull(createdAt, "Data de criação");
        this.updatedAt = Guard.notNull(updatedAt, "Data de atualização");
    }

    public static GrowDiary start(GrowerId ownerId, String title, Strain strain, FloweringType floweringType,
                                  SeedSex seedSex, GrowMedium medium, Visibility visibility,
                                  LocalDate startedOn, Clock clock) {
        Guard.notNull(startedOn, "Data de início");
        if (startedOn.isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de início não pode estar no futuro");
        }
        Instant now = clock.instant();
        return new GrowDiary(GrowDiaryId.newId(), ownerId, title, strain, floweringType, seedSex, medium,
                DiaryStatus.IN_PROGRESS, visibility, startedOn, null, GrowthStage.initialFor(seedSex), null, now, now);
    }

    /** Reconstitui um diário já persistido; não aplica regras de criação. */
    public static GrowDiary restore(GrowDiaryId id, GrowerId ownerId, String title, Strain strain,
                                    FloweringType floweringType, SeedSex seedSex, GrowMedium medium,
                                    DiaryStatus status, Visibility visibility, LocalDate startedOn,
                                    LocalDate endedOn, GrowthStage currentStage, LocalDate lastUpdateOn,
                                    Instant createdAt, Instant updatedAt) {
        return new GrowDiary(id, ownerId, title, strain, floweringType, seedSex, medium, status, visibility,
                startedOn, endedOn, currentStage, lastUpdateOn, createdAt, updatedAt);
    }

    /**
     * Registra que um update foi publicado. O estágio atual só muda se o update for o mais recente,
     * para que um update retroativo (ex.: fotos antigas) não "volte" o cultivo de fase.
     */
    public void recordUpdate(GrowerId author, GrowthStage stage, LocalDate recordedOn, Clock clock) {
        if (!isOwnedBy(author)) {
            throw new DomainException("Apenas o autor pode atualizar o diário");
        }
        if (!isInProgress()) {
            throw new DomainException("Diário encerrado não recebe novos updates");
        }
        Guard.notNull(stage, "Estágio");
        Guard.notNull(recordedOn, "Data");
        if (recordedOn.isBefore(startedOn) || recordedOn.isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data do update deve estar entre o início do cultivo e hoje");
        }
        if (lastUpdateOn == null || !recordedOn.isBefore(lastUpdateOn)) {
            currentStage = stage;
            lastUpdateOn = recordedOn;
        }
        touch(clock);
    }

    /** Recalcula o progresso depois que um update é removido, a partir do update mais recente que restou. */
    public void rebuildProgress(Optional<DiaryUpdate> latestRemaining, Clock clock) {
        currentStage = latestRemaining.map(DiaryUpdate::stage).orElse(GrowthStage.initialFor(seedSex));
        lastUpdateOn = latestRemaining.map(DiaryUpdate::recordedOn).orElse(null);
        touch(clock);
    }

    /** Diários privados só existem para o autor. */
    public boolean isVisibleTo(Optional<GrowerId> viewer) {
        return visibility == Visibility.PUBLIC || viewer.map(this::isOwnedBy).orElse(false);
    }

    public void rename(String newTitle, Clock clock) {
        this.title = validTitle(newTitle);
        touch(clock);
    }

    public void changeVisibility(Visibility newVisibility, Clock clock) {
        this.visibility = Guard.notNull(newVisibility, "Visibilidade");
        touch(clock);
    }

    public void complete(LocalDate harvestedOn, Clock clock) {
        finish(DiaryStatus.COMPLETED, harvestedOn, clock);
    }

    public void cancel(Clock clock) {
        finish(DiaryStatus.CANCELLED, LocalDate.now(clock), clock);
    }

    public GrowAge ageOn(LocalDate date) {
        return GrowAge.of(startedOn, endedOn, date);
    }

    public boolean isOwnedBy(GrowerId grower) {
        return ownerId.equals(grower);
    }

    public boolean isInProgress() {
        return status == DiaryStatus.IN_PROGRESS;
    }

    private void finish(DiaryStatus finalStatus, LocalDate date, Clock clock) {
        if (!isInProgress()) {
            throw new DomainException("Só é possível encerrar um diário em andamento");
        }
        Guard.notNull(date, "Data de encerramento");
        if (date.isBefore(startedOn) || date.isAfter(LocalDate.now(clock))) {
            throw new DomainException("A data de encerramento deve estar entre o início do cultivo e hoje");
        }
        this.status = finalStatus;
        this.endedOn = date;
        touch(clock);
    }

    private void touch(Clock clock) {
        this.updatedAt = clock.instant();
    }

    private static String validTitle(String title) {
        return Guard.lengthBetween(Guard.notBlank(title, "Título"), 1, TITLE_MAX_LENGTH, "Título");
    }

    public GrowDiaryId id() {
        return id;
    }

    public GrowerId ownerId() {
        return ownerId;
    }

    public String title() {
        return title;
    }

    public Strain strain() {
        return strain;
    }

    public FloweringType floweringType() {
        return floweringType;
    }

    public SeedSex seedSex() {
        return seedSex;
    }

    public GrowMedium medium() {
        return medium;
    }

    public DiaryStatus status() {
        return status;
    }

    public Visibility visibility() {
        return visibility;
    }

    public LocalDate startedOn() {
        return startedOn;
    }

    public LocalDate endedOn() {
        return endedOn;
    }

    public GrowthStage currentStage() {
        return currentStage;
    }

    public LocalDate lastUpdateOn() {
        return lastUpdateOn;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
