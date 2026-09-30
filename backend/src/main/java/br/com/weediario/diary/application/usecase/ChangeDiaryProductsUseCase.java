package br.com.weediario.diary.application.usecase;

import br.com.weediario.diary.application.DiaryAccess;
import br.com.weediario.diary.application.port.CatalogLookup;
import br.com.weediario.diary.domain.model.GrowDiary;
import br.com.weediario.diary.domain.model.ProductSnapshot;
import br.com.weediario.diary.domain.model.SetupSnapshot;
import br.com.weediario.diary.domain.repository.GrowDiaryRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Troca os produtos usados no cultivo. Os produtos precisam existir no catálogo da comunidade (a marca ou o
 * produto novo é cadastrado antes, pelo autocomplete), para contarem no ranking de "Produtos".
 */
public final class ChangeDiaryProductsUseCase {

    private final DiaryAccess access;
    private final GrowDiaryRepository diaries;
    private final CatalogLookup catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeDiaryProductsUseCase(DiaryAccess access, GrowDiaryRepository diaries, CatalogLookup catalog,
                                      UnitOfWork unitOfWork, Clock clock) {
        this.access = access;
        this.diaries = diaries;
        this.catalog = catalog;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<ProductSnapshot> execute(UUID diaryId, UUID ownerId, List<UUID> productIds) {
        return unitOfWork.execute(() -> {
            GrowDiary diary = access.ownedBy(diaryId, ownerId);
            diary.changeProducts(catalog.products(productIds == null ? List.of() : productIds), clock);
            return diaries.save(diary).setup().map(SetupSnapshot::products).orElse(List.of());
        });
    }
}
