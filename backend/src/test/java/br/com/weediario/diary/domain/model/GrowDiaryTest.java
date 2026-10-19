package br.com.weediario.diary.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrowDiaryTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T15:00:00Z"), SAO_PAULO);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    @Test
    void startsInProgress() {
        GrowDiary diary = startedOn(TODAY.minusDays(10));

        assertThat(diary.status()).isEqualTo(DiaryStatus.IN_PROGRESS);
        assertThat(diary.endedOn()).isNull();
    }

    @Test
    void rejectsStartDateInTheFuture() {
        assertThatThrownBy(() -> startedOn(TODAY.plusDays(1)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("futuro");
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> GrowDiary.start(owner(), "  ", strain(), FloweringType.AUTOFLOWER, SeedSex.FEMINIZED,
                GrowMedium.LIVING_SOIL, Visibility.PUBLIC, TODAY, CLOCK))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void countsDaysAndWeeksFromStart() {
        GrowDiary diary = startedOn(TODAY.minusDays(14));

        GrowAge age = diary.ageOn(TODAY);

        assertThat(age.day()).isEqualTo(15);
        assertThat(age.week()).isEqualTo(3);
    }

    @Test
    void firstDayIsDayOneOfWeekOne() {
        GrowAge age = startedOn(TODAY).ageOn(TODAY);

        assertThat(age).isEqualTo(new GrowAge(1, 1));
    }

    @Test
    void ageStopsCountingAfterCompletion() {
        GrowDiary diary = startedOn(TODAY.minusDays(90));
        diary.complete(TODAY.minusDays(20), CLOCK);

        assertThat(diary.ageOn(TODAY).day()).isEqualTo(71);
    }

    @Test
    void cannotFinishTwice() {
        GrowDiary diary = startedOn(TODAY.minusDays(30));
        diary.cancel(CLOCK);

        assertThatThrownBy(() -> diary.complete(TODAY, CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void completionDateMustBeWithinTheGrow() {
        GrowDiary diary = startedOn(TODAY.minusDays(30));

        assertThatThrownBy(() -> diary.complete(TODAY.minusDays(31), CLOCK)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> diary.complete(TODAY.plusDays(1), CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void blankBreederIsTreatedAsUnknown() {
        assertThat(new Strain("Gorilla Glue #4", "   ").breederName()).isEmpty();
    }

    @Test
    void communityDiaryIsVisibleOnlyToSignedInViewers() {
        GrowerId owner = owner();
        GrowerId other = owner();
        GrowDiary diary = GrowDiary.start(owner, "Comunidade", strain(), FloweringType.PHOTOPERIOD, SeedSex.FEMINIZED,
                GrowMedium.LIVING_SOIL, Visibility.COMMUNITY, TODAY, CLOCK);

        assertThat(diary.isVisibleTo(Optional.empty())).as("deslogado").isFalse();
        assertThat(diary.isVisibleTo(Optional.of(other))).as("outro logado").isTrue();
        assertThat(diary.isVisibleTo(Optional.of(owner))).as("dono").isTrue();
    }

    @Test
    void moderationHoldBlocksMakingDiaryVisibleAgain() {
        GrowDiary diary = startedOn(TODAY.minusDays(5));
        diary.holdForModeration(CLOCK);

        assertThatThrownBy(() -> diary.changeVisibility(Visibility.PUBLIC, CLOCK)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> diary.changeVisibility(Visibility.COMMUNITY, CLOCK)).isInstanceOf(DomainException.class);
        // Continuar privado é permitido.
        diary.changeVisibility(Visibility.PRIVATE, CLOCK);
        assertThat(diary.isVisibleTo(Optional.empty())).isFalse();
    }

    private static GrowDiary startedOn(LocalDate date) {
        return GrowDiary.start(owner(), "Tenda 60x60 - GG4", strain(), FloweringType.PHOTOPERIOD, SeedSex.FEMINIZED,
                GrowMedium.LIVING_SOIL, Visibility.PUBLIC, date, CLOCK);
    }

    private static GrowerId owner() {
        return new GrowerId(UUID.randomUUID());
    }

    private static Strain strain() {
        return new Strain("Gorilla Glue #4", "GG Strains");
    }
}
