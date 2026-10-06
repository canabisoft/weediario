package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.application.exception.GrowerNotAllowedException;
import br.com.weediario.diary.domain.model.DiaryStatus;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowDiaryId;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.ProductSnapshot;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.diary.domain.repository.GrowSetupRepository;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateGrowDiaryUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-29T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final UUID ACTIVE_GROWER = UUID.randomUUID();

    private final InMemoryGrowDiaryRepository diaries = new InMemoryGrowDiaryRepository();
    private final CreateGrowDiaryUseCase useCase = new CreateGrowDiaryUseCase(
            diaries, new NoSetups(), new FakeCatalog(), grower -> Set.of(ACTIVE_GROWER).contains(grower.value()),
            (diaryId, reason) -> { }, (accountId, limit) -> { }, new DirectUnitOfWork(), CLOCK);

    @Test
    void createsDiaryWithDefaults() {
        GrowDiaryView view = useCase.execute(command(ACTIVE_GROWER, null, null));

        assertThat(view.status()).isEqualTo(DiaryStatus.IN_PROGRESS);
        assertThat(view.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(view.startedOn()).isEqualTo(LocalDate.now(CLOCK));
        assertThat(view.currentDay()).isEqualTo(1);
        assertThat(diaries.store).hasSize(1);
    }

    @Test
    void keepsExplicitStartDateAndComputesWeek() {
        GrowDiaryView view = useCase.execute(command(ACTIVE_GROWER, Visibility.PRIVATE, LocalDate.now(CLOCK).minusDays(21)));

        assertThat(view.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(view.currentWeek()).isEqualTo(4);
    }

    @Test
    void rejectsInactiveOrUnknownGrower() {
        assertThatThrownBy(() -> useCase.execute(command(UUID.randomUUID(), null, null)))
                .isInstanceOf(GrowerNotAllowedException.class);
        assertThat(diaries.store).isEmpty();
    }

    @Test
    void enforcesLimitOfDiariesInProgress() {
        for (int i = 0; i < CreateGrowDiaryUseCase.MAX_DIARIES_IN_PROGRESS; i++) {
            useCase.execute(command(ACTIVE_GROWER, null, null));
        }

        assertThatThrownBy(() -> useCase.execute(command(ACTIVE_GROWER, null, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Limite");
    }

    private static CreateGrowDiaryUseCase.Command command(UUID owner, Visibility visibility, LocalDate startedOn) {
        return new CreateGrowDiaryUseCase.Command(owner, "Auto na tenda 80x80", "Northern Lights Auto", null,
                FloweringType.AUTOFLOWER, SeedSex.FEMINIZED, GrowMedium.MINERAL, visibility, startedOn, null, null);
    }

    /** Catálogo em memória: toda strain digitada vira (ou reusa) um item com id próprio. */
    private static final class FakeCatalog implements CatalogLookup {
        private final Map<String, CatalogStrainRef> strains = new HashMap<>();

        @Override
        public CatalogStrainRef strain(UUID strainId) {
            return strains.values().stream().filter(ref -> ref.strainId().equals(strainId)).findFirst().orElseThrow();
        }

        @Override
        public CatalogStrainRef ensureStrain(String name, String breeder, UUID userId) {
            return strains.computeIfAbsent(name.toLowerCase(),
                    key -> new CatalogStrainRef(UUID.randomUUID(), name, null, breeder));
        }

        @Override
        public List<ProductSnapshot> products(List<UUID> productIds) {
            return List.of();
        }
    }

    private static final class NoSetups implements GrowSetupRepository {
        @Override
        public GrowSetup save(GrowSetup setup) {
            return setup;
        }

        @Override
        public Optional<GrowSetup> findById(GrowSetupId id) {
            return Optional.empty();
        }

        @Override
        public List<GrowSetup> findByOwner(GrowerId ownerId) {
            return List.of();
        }

        @Override
        public long countByOwner(GrowerId ownerId) {
            return 0;
        }

        @Override
        public void delete(GrowSetupId id) {
        }
    }

    private static final class DirectUnitOfWork implements UnitOfWork {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    }

    private static final class InMemoryGrowDiaryRepository implements GrowDiaryRepository {
        private final Map<GrowDiaryId, GrowDiary> store = new HashMap<>();

        @Override
        public GrowDiary save(GrowDiary diary) {
            store.put(diary.id(), diary);
            return diary;
        }

        @Override
        public Optional<GrowDiary> findById(GrowDiaryId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public long countInProgressByOwner(GrowerId ownerId) {
            return store.values().stream().filter(d -> d.isOwnedBy(ownerId) && d.isInProgress()).count();
        }
    }
}
