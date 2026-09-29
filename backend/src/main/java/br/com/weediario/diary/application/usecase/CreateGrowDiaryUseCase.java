package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.GrowDiaryView;
import br.com.weediario.diary.application.exception.GrowerNotAllowedException;
import br.com.weediario.diary.application.port.GrowerDirectory;
import br.com.weediario.diary.domain.model.FloweringType;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.GrowMedium;
import br.com.weediario.diary.domain.model.GrowerId;
import br.com.weediario.diary.domain.model.SeedSex;
import br.com.weediario.diary.domain.model.Strain;
import br.com.weediario.diary.domain.model.Visibility;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
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
 */
public final class CreateGrowDiaryUseCase {

    public static final int MAX_DIARIES_IN_PROGRESS = 20;

    private final GrowDiaryRepository diaries;
    private final GrowerDirectory growers;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateGrowDiaryUseCase(GrowDiaryRepository diaries, GrowerDirectory growers,
                                  UnitOfWork unitOfWork, Clock clock) {
        this.diaries = diaries;
        this.growers = growers;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public GrowDiaryView execute(Command command) {
        GrowerId owner = new GrowerId(command.ownerId());

        return unitOfWork.execute(() -> {
            ensureCanOpenNewDiary(owner);
            GrowDiary diary = GrowDiary.start(
                    owner,
                    command.title(),
                    new Strain(command.strainName(), command.breeder()),
                    command.floweringType(),
                    command.seedSex(),
                    command.medium(),
                    command.visibility() == null ? Visibility.PUBLIC : command.visibility(),
                    command.startedOn() == null ? LocalDate.now(clock) : command.startedOn(),
                    clock);
            return GrowDiaryView.of(diaries.save(diary), LocalDate.now(clock));
        });
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
            LocalDate startedOn) {
    }
}
