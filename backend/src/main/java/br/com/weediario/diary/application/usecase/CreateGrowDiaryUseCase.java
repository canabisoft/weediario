package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.application.exception.GrowerNotAllowedException;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.application.port.DiaryModeration;
import br.com.weediario.shared.domain.CommerceFilter;
import br.com.weediario.shared.domain.CommerceFilter.Verdict;
import br.com.weediario.diary.application.port.CatalogLookup.CatalogStrainRef;
import br.com.weediario.diary.application.port.GrowerDirectory;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.GrowSetupId;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.SetupSnapshot;
import br.com.weediario.diary.domain.model.Strain;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.diary.domain.repository.GrowSetupRepository;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Abre um novo diário de cultivo para o grower autenticado.
 * <p>
 * Regras de aplicação: o autor precisa ser um grower ativo e respeitar o limite de diários em andamento
 * (proteção contra spam/abuso). As invariantes do diário em si ficam no aggregate {@link GrowDiary}.
 * <p>
 * Com um setup escolhido, o diário recebe uma cópia dele e herda o meio de cultivo se o grower não informar outro.
 * <p>
 * A strain vem do catálogo: escolhida no autocomplete ({@code strainId}) ou, se só o nome for informado,
 * achada pelo nome ou cadastrada, sem duplicar.
 */
public final class CreateGrowDiaryUseCase {

    public static final int MAX_DIARIES_IN_PROGRESS = 20;

    private final GrowDiaryRepository diaries;
    private final GrowSetupRepository setups;
    private final CatalogLookup catalog;
    private final GrowerDirectory growers;
    private final DiaryModeration moderation;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateGrowDiaryUseCase(GrowDiaryRepository diaries, GrowSetupRepository setups, CatalogLookup catalog,
                                  GrowerDirectory growers, DiaryModeration moderation, UnitOfWork unitOfWork,
                                  Clock clock) {
        this.diaries = diaries;
        this.setups = setups;
        this.catalog = catalog;
        this.growers = growers;
        this.moderation = moderation;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public GrowDiaryView execute(Command command) {
        GrowerId owner = new GrowerId(command.ownerId());
        Visibility visibility = command.visibility() == null ? Visibility.PUBLIC : command.visibility();
        // Título de diário público passa pelo filtro anti-comércio; o de diário privado só quando ficar público.
        Verdict verdict = visibility == Visibility.PUBLIC ? CommerceFilter.requireAllowedOrHeld(command.title())
                : Verdict.ALLOWED;

        return unitOfWork.execute(() -> {
            ensureCanOpenNewDiary(owner);
            Strain strain = strainOf(command);
            SetupSnapshot setup = command.setupId() == null ? null : ownedSetup(owner, command.setupId());
            GrowMedium medium = command.medium() != null ? command.medium()
                    : setup == null ? null : setup.spec().medium();
            if (medium == null) {
                throw new DomainException("Meio de cultivo é obrigatório");
            }
            GrowDiary diary = GrowDiary.start(
                    owner,
                    command.title(),
                    strain,
                    command.floweringType(),
                    command.seedSex(),
                    medium,
                    visibility,
                    command.startedOn() == null ? LocalDate.now(clock) : command.startedOn(),
                    setup,
                    clock);
            if (verdict.held()) {
                diary.holdForModeration(clock);
            }
            GrowDiaryView view = GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
            if (verdict.held()) {
                moderation.heldByFilter(diary.id().value(), verdict.reason());
            }
            return view;
        });
    }

    private Strain strainOf(Command command) {
        CatalogStrainRef ref;
        if (command.strainId() != null) {
            ref = catalog.strain(command.strainId());
        } else if (command.strainName() != null && !command.strainName().isBlank()) {
            ref = catalog.ensureStrain(command.strainName(), command.breeder(), command.ownerId());
        } else {
            throw new DomainException("Informe a strain");
        }
        return new Strain(ref.name(), ref.breederName(), ref.strainId(), ref.breederId());
    }

    private SetupSnapshot ownedSetup(GrowerId owner, UUID setupId) {
        GrowSetup setup = setups.findById(new GrowSetupId(setupId))
                .filter(found -> found.isOwnedBy(owner))
                .orElseThrow(() -> new ResourceNotFoundException("Setup"));
        return setup.snapshot(catalog.products(setup.productIds()));
    }

    private void ensureCanOpenNewDiary(GrowerId owner) {
        if (!growers.isActiveGrower(owner)) {
            throw new GrowerNotAllowedException();
        }
        if (diaries.countInProgressByOwner(owner) >= MAX_DIARIES_IN_PROGRESS) {
            throw new DomainException(
                    "Limite de " + MAX_DIARIES_IN_PROGRESS + " diários em andamento atingido. Conclua ou cancele um diário antes.");
        }
    }

    public record Command(
            UUID ownerId,
            String title,
            String strainName,
            String breeder,
            FloweringType floweringType,
            SeedSex seedSex,
            GrowMedium medium,
            Visibility visibility,
            LocalDate startedOn,
            UUID setupId,
            UUID strainId) {
    }
}
