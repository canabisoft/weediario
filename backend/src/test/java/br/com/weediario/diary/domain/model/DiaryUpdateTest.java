package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryUpdateTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final GrowerId OWNER = new GrowerId(UUID.randomUUID());

    @Test
    void newDiaryStartsAtGerminationAndClonesAtVegetative() {
        assertThat(diary(SeedSex.FEMINIZED).currentStage()).isEqualTo(GrowthStage.GERMINATION);
        assertThat(diary(SeedSex.CLONE).currentStage()).isEqualTo(GrowthStage.VEGETATIVE);
    }

    @Test
    void latestUpdateMovesDiaryToItsStage() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);

        DiaryUpdate.publish(diary, OWNER, GrowthStage.FLOWERING, TODAY.minusDays(1), "Virou pra flora", null, List.of(), CLOCK);

        assertThat(diary.currentStage()).isEqualTo(GrowthStage.FLOWERING);
        assertThat(diary.lastUpdateOn()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void retroactiveUpdateDoesNotRewindStage() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);
        DiaryUpdate.publish(diary, OWNER, GrowthStage.FLOWERING, TODAY, "flora", null, List.of(), CLOCK);

        DiaryUpdate.publish(diary, OWNER, GrowthStage.VEGETATIVE, TODAY.minusDays(20), "fotos antigas", null, List.of(), CLOCK);

        assertThat(diary.currentStage()).isEqualTo(GrowthStage.FLOWERING);
    }

    @Test
    void rebuildProgressFallsBackToInitialStage() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);
        DiaryUpdate.publish(diary, OWNER, GrowthStage.FLOWERING, TODAY, "flora", null, List.of(), CLOCK);

        diary.rebuildProgress(Optional.empty(), CLOCK);

        assertThat(diary.currentStage()).isEqualTo(GrowthStage.GERMINATION);
        assertThat(diary.lastUpdateOn()).isNull();
    }

    @Test
    void onlyOwnerCanPublish() {
        GrowerId stranger = new GrowerId(UUID.randomUUID());

        assertThatThrownBy(() -> DiaryUpdate.publish(diary(SeedSex.FEMINIZED), stranger, GrowthStage.VEGETATIVE,
                TODAY, "invasão", null, List.of(), CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void finishedDiaryRejectsUpdates() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);
        diary.complete(TODAY, CLOCK);

        assertThatThrownBy(() -> DiaryUpdate.publish(diary, OWNER, GrowthStage.DRYING_CURING, TODAY, "cura",
                null, List.of(), CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsDatesOutsideTheGrow() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);

        assertThatThrownBy(() -> DiaryUpdate.publish(diary, OWNER, GrowthStage.VEGETATIVE, TODAY.plusDays(1), "futuro",
                null, List.of(), CLOCK)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> DiaryUpdate.publish(diary, OWNER, GrowthStage.VEGETATIVE, TODAY.minusDays(60), "antes",
                null, List.of(), CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsEmptyUpdate() {
        assertThatThrownBy(() -> DiaryUpdate.publish(diary(SeedSex.FEMINIZED), OWNER, GrowthStage.VEGETATIVE, TODAY,
                "   ", GrowMetrics.EMPTY, List.of(), CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("foto, uma anotação ou alguma métrica");
    }

    @Test
    void metricsOnlyUpdateIsAllowedAndRounded() {
        GrowMetrics metrics = new GrowMetrics(new BigDecimal("24.56"), new BigDecimal("55"), new BigDecimal("6.234"), null);

        DiaryUpdate update = DiaryUpdate.publish(diary(SeedSex.FEMINIZED), OWNER, GrowthStage.VEGETATIVE, TODAY,
                null, metrics, null, CLOCK);

        assertThat(update.metrics().temperatureCelsius()).isEqualByComparingTo("24.6");
        assertThat(update.metrics().wateringPh()).isEqualByComparingTo("6.23");
        assertThat(update.note()).isNull();
    }

    @Test
    void rejectsImpossibleMetrics() {
        assertThatThrownBy(() -> new GrowMetrics(null, null, new BigDecimal("70"), null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("pH");
    }

    @Test
    void deduplicatesPhotosAndLimitsCount() {
        UUID photo = UUID.randomUUID();
        DiaryUpdate update = DiaryUpdate.publish(diary(SeedSex.FEMINIZED), OWNER, GrowthStage.VEGETATIVE, TODAY,
                null, null, List.of(photo, photo), CLOCK);
        assertThat(update.photoIds()).containsExactly(photo);

        List<UUID> tooMany = Collections.nCopies(DiaryUpdate.MAX_PHOTOS + 1, 0).stream().map(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> DiaryUpdate.publish(diary(SeedSex.FEMINIZED), OWNER, GrowthStage.VEGETATIVE, TODAY,
                null, null, tooMany, CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void stripsInvisibleControlCharactersFromNote() {
        DiaryUpdate update = DiaryUpdate.publish(diary(SeedSex.FEMINIZED), OWNER, GrowthStage.VEGETATIVE, TODAY,
                "rega\u0000 feita\u0007\nsem pragas", null, List.of(), CLOCK);

        assertThat(update.note()).isEqualTo("rega feita\nsem pragas");
    }

    @Test
    void privateDiaryIsVisibleOnlyToOwner() {
        GrowDiary diary = diary(SeedSex.FEMINIZED);
        diary.changeVisibility(Visibility.PRIVATE, CLOCK);

        assertThat(diary.isVisibleTo(Optional.of(OWNER))).isTrue();
        assertThat(diary.isVisibleTo(Optional.of(new GrowerId(UUID.randomUUID())))).isFalse();
        assertThat(diary.isVisibleTo(Optional.empty())).isFalse();
    }

    private static GrowDiary diary(SeedSex seedSex) {
        return GrowDiary.start(OWNER, "Tenda 80x80", new Strain("Amnesia Haze", null), FloweringType.PHOTOPERIOD,
                seedSex, GrowMedium.INERT, Visibility.PUBLIC, TODAY.minusDays(30), CLOCK);
    }
}
