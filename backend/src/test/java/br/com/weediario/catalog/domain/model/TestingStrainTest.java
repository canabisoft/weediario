package br.com.weediario.catalog.domain.model;

import br.com.weediario.catalog.domain.model.CatalogEnums.Source;
import br.com.weediario.catalog.domain.model.CatalogEnums.Status;
import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestingStrainTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Breeder BREEDER = Breeder.register("Banco Teste Seeds", Source.COMMUNITY, null, CLOCK);

    @Test
    void breederSeedsStartInTesting() {
        CatalogStrain strain = CatalogStrain.registerForTesting("Nova Kush", BREEDER, StrainTraits.EMPTY,
                UUID.randomUUID(), CLOCK);

        assertThat(strain.status()).isEqualTo(Status.TESTING);
        assertThat(strain.source()).isEqualTo(Source.BREEDER);
        assertThat(strain.promotedAt()).isNull();
        assertThatThrownBy(() -> CatalogStrain.registerForTesting("Sem Banco", null, StrainTraits.EMPTY, null, CLOCK))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void needsBreederReviewThreeTestersAndOneReviewToBecomeOfficial() {
        CatalogStrain strain = CatalogStrain.registerForTesting("Nova Kush", BREEDER, StrainTraits.EMPTY, null, CLOCK);

        assertThat(strain.promote(new TestingProgress(2, 5, true), CLOCK)).isFalse();
        assertThat(strain.promote(new TestingProgress(10, 0, true), CLOCK)).isFalse();
        // Cultivadores e avaliações de fora completos, mas o breeder ainda não colheu e avaliou: não entra.
        assertThat(strain.promote(new TestingProgress(10, 5, false), CLOCK)).isFalse();
        assertThat(strain.testing()).isTrue();

        assertThat(strain.promote(
                new TestingProgress(TestingProgress.TESTERS_REQUIRED, TestingProgress.REVIEWS_REQUIRED, true),
                CLOCK)).isTrue();
        assertThat(strain.status()).isEqualTo(Status.VERIFIED);
        assertThat(strain.promotedAt()).isEqualTo(CLOCK.instant());
        // Já promovida: nada muda.
        assertThat(strain.promote(new TestingProgress(9, 9, true), CLOCK)).isFalse();
    }

    @Test
    void onlyTestingStrainsArePromoted() {
        CatalogStrain community = CatalogStrain.register("Velha Kush", BREEDER, StrainTraits.EMPTY, Source.COMMUNITY,
                null, CLOCK);

        assertThat(community.promote(new TestingProgress(3, 1, true), CLOCK)).isFalse();
        assertThat(community.status()).isEqualTo(Status.UNVERIFIED);
        assertThat(community.promotedAt()).isNull();
    }

    @Test
    void moderatorCannotVerifyATestingStrainByHand() {
        CatalogStrain strain = CatalogStrain.registerForTesting("Nova Kush", BREEDER, StrainTraits.EMPTY, null, CLOCK);

        assertThatThrownBy(() -> strain.verify(CLOCK))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("3 cultivadores");
        assertThat(strain.testing()).isTrue();
    }

    @Test
    void progressCannotBeNegative() {
        assertThatThrownBy(() -> new TestingProgress(-1, 0, true)).isInstanceOf(DomainException.class);
        assertThat(new TestingProgress(3, 1, true).complete()).isTrue();
        assertThat(new TestingProgress(3, 0, true).complete()).isFalse();
        // Sem a colheita/avaliação do breeder, não completa mesmo com cultivadores e avaliações de fora.
        assertThat(new TestingProgress(3, 1, false).complete()).isFalse();
    }

    @Test
    void representationNeedsANoteForTheModeratorsAndIsApprovedOnce() {
        UUID breederId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> BreederRepresentative.request(breederId, userId, "oi", CLOCK))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> BreederRepresentative.request(breederId, userId, "x".repeat(501), CLOCK))
                .isInstanceOf(DomainException.class);

        BreederRepresentative request = BreederRepresentative.request(breederId, userId,
                "  Sou o dono, veja o site oficial do banco  ", CLOCK);
        assertThat(request.approved()).isFalse();
        assertThat(request.note()).isEqualTo("Sou o dono, veja o site oficial do banco");
        assertThat(request.decidedAt()).isNull();

        BreederRepresentative approved = request.approve(CLOCK);
        assertThat(approved.approved()).isTrue();
        assertThat(approved.decidedAt()).isEqualTo(CLOCK.instant());
        assertThatThrownBy(() -> approved.approve(CLOCK)).isInstanceOf(DomainException.class);
    }

    @Test
    void onlyIndependentBreedersMayBeCreditedAndCanOptOut() {
        UUID breederId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String note = "Diários de polinização no meu perfil";

        assertThatThrownBy(() -> BreederRepresentative.request(breederId, userId, note,
                CatalogEnums.BreederKind.SEEDBANK, true, CLOCK)).isInstanceOf(DomainException.class);

        BreederRepresentative credited = BreederRepresentative.request(breederId, userId, note,
                CatalogEnums.BreederKind.INDEPENDENT, true, CLOCK);
        assertThat(credited.publicCredit()).isTrue();
        assertThat(credited.approve(CLOCK).withPublicCredit(false).publicCredit()).isFalse();
        // Sem tipo informado (pedidos antigos): banco de sementes, anônimo.
        assertThat(BreederRepresentative.request(breederId, userId, note, CLOCK).claimedKind())
                .isEqualTo(CatalogEnums.BreederKind.SEEDBANK);
    }

    @Test
    void generationIsFilledByTheCommunityOnlyWhenMissing() {
        StrainTraits informed = new StrainTraits(null, null, null, null, null, null, null,
                CatalogEnums.StrainGeneration.F1);
        StrainTraits contribution = new StrainTraits(null, null, null, null, null, null, null,
                CatalogEnums.StrainGeneration.IBL);
        assertThat(informed.fillMissingFrom(contribution).generation()).isEqualTo(CatalogEnums.StrainGeneration.F1);
        assertThat(StrainTraits.EMPTY.fillMissingFrom(contribution).generation())
                .isEqualTo(CatalogEnums.StrainGeneration.IBL);
    }
}
