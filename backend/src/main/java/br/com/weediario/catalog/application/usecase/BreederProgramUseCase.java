package br.com.weediario.catalog.application.usecase;

import br.com.weediario.catalog.application.CatalogItem;
import br.com.weediario.catalog.domain.model.Breeder;
import br.com.weediario.catalog.domain.model.BreederRepresentative;
import br.com.weediario.catalog.domain.model.CatalogKey;
import br.com.weediario.catalog.domain.model.CatalogStrain;
import br.com.weediario.catalog.domain.model.StrainTraits;
import br.com.weediario.catalog.domain.repository.BreederRepresentativeRepository;
import br.com.weediario.catalog.domain.repository.CatalogRepository;
import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Programa de breeders: quem representa um breeder do catálogo (aprovado pela moderação) cadastra as sementes dele,
 * que ficam "em teste" até a comunidade cultivar e avaliar (ver {@link PromoteTestingStrainsUseCase}).
 */
public final class BreederProgramUseCase {

    private final CatalogRepository catalog;
    private final BreederRepresentativeRepository representatives;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public BreederProgramUseCase(CatalogRepository catalog, BreederRepresentativeRepository representatives,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.catalog = catalog;
        this.representatives = representatives;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Pede para representar um breeder. A nota (prova de vínculo) só é lida pela moderação. */
    public void requestRepresentation(UUID userId, UUID breederId, String note) {
        unitOfWork.run(() -> {
            breeder(breederId);
            if (representatives.find(breederId, userId).isPresent()) {
                throw new DomainException("Você já pediu para representar este breeder");
            }
            if (representatives.countPendingBy(userId) >= BreederRepresentative.MAX_PENDING_PER_USER) {
                throw new DomainException("Aguarde a moderação responder os pedidos que você já fez");
            }
            representatives.save(BreederRepresentative.request(breederId, userId, note, clock));
        });
    }

    /** Desiste do pedido ou deixa de representar o breeder. */
    public void withdraw(UUID userId, UUID breederId) {
        unitOfWork.run(() -> {
            if (!representatives.delete(breederId, userId)) {
                throw new ResourceNotFoundException("Pedido");
            }
        });
    }

    /**
     * Cadastra uma semente do breeder para teste. Nome e características são públicos: passam pelo filtro
     * anti-comércio e contam no limite diário de cadastros do catálogo.
     */
    public CatalogItem registerStrain(UUID userId, UUID breederId, String name, StrainTraits traits) {
        StrainTraits safeTraits = traits == null ? StrainTraits.EMPTY : traits;
        CommerceFilter.requireAllowed(name, safeTraits.genetics(), String.join(" ", safeTraits.flavors()),
                String.join(" ", safeTraits.effects()));
        return unitOfWork.execute(() -> {
            Breeder breeder = breeder(breederId);
            boolean approved = representatives.find(breederId, userId).map(BreederRepresentative::approved).orElse(false);
            if (!approved) {
                throw new DomainException("Só representantes aprovados pela moderação cadastram sementes deste breeder");
            }
            if (catalog.findStrainByKey(CatalogKey.of(name), breeder.nameKey()).isPresent()) {
                throw new DomainException("Esta strain já está no catálogo deste breeder. "
                        + "Complete as informações na página dela.");
            }
            if (catalog.countCreatedBy(userId, clock.instant().minus(Duration.ofDays(1)))
                    >= ResolveCatalogUseCase.MAX_CREATIONS_PER_DAY) {
                throw new DomainException("Limite diário de cadastros no catálogo atingido. Tente amanhã.");
            }
            return CatalogItem.of(catalog.saveStrain(
                    CatalogStrain.registerForTesting(name, breeder, safeTraits, userId, clock)));
        });
    }

    public void approve(UUID breederId, UUID userId, boolean moderator) {
        requireModerator(moderator);
        unitOfWork.run(() -> representatives.save(representatives.find(breederId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido")).approve(clock)));
    }

    /** Recusa um pedido ou revoga um representante aprovado (apaga o vínculo). */
    public void reject(UUID breederId, UUID userId, boolean moderator) {
        requireModerator(moderator);
        withdraw(userId, breederId);
    }

    private Breeder breeder(UUID id) {
        return catalog.findBreeder(id).orElseThrow(() -> new ResourceNotFoundException("Breeder"));
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }
}
