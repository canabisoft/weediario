package br.com.weediario.nutrition.application.usecase;

import br.com.weediario.nutrition.application.port.GrowDiaries;
import br.com.weediario.nutrition.application.port.GrowDiaries.DiaryRef;
import br.com.weediario.nutrition.application.query.RecipeReadModel;
import br.com.weediario.nutrition.application.query.RecipeReadModel.Recommendation;
import br.com.weediario.nutrition.domain.model.RecipeKind;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recomendação automática de receitas, combinada com a da comunidade (estrelas):
 * <ol>
 *   <li>tipo certo para o cultivo: mineral/inerte → receitas minerais, solo vivo → orgânicas;</li>
 *   <li>receitas com produtos que o grower já usa nos cultivos e setups primeiro (dá para aproveitar o que tem);</li>
 *   <li>depois as mais bem avaliadas (média ponderada), mais usadas em cultivos e mais replicadas.</li>
 * </ol>
 * Sem login, vira simplesmente o topo do ranking.
 */
public final class RecommendRecipesUseCase {

    public static final int DEFAULT_LIMIT = 6;

    private final RecipeReadModel readModel;
    private final GrowDiaries diaries;
    private final UnitOfWork unitOfWork;

    public RecommendRecipesUseCase(RecipeReadModel readModel, GrowDiaries diaries, UnitOfWork unitOfWork) {
        this.readModel = readModel;
        this.diaries = diaries;
        this.unitOfWork = unitOfWork;
    }

    /**
     * @param diaryId cultivo do próprio grower para o qual se quer sugestões (define o tipo e exclui as já vinculadas)
     * @param kind    tipo pedido explicitamente; com {@code diaryId}, o meio do cultivo prevalece
     */
    public List<Recommendation> recommend(Optional<UUID> viewerId, Optional<UUID> diaryId, RecipeKind kind, int limit) {
        int size = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 20);
        return unitOfWork.execute(() -> {
            RecipeKind effectiveKind = kind;
            UUID excludeDiary = null;
            if (diaryId.isPresent()) {
                DiaryRef diary = diaries.find(diaryId.get())
                        .filter(found -> viewerId.map(found.ownerId()::equals).orElse(false))
                        .orElseThrow(() -> new ResourceNotFoundException("Diário"));
                effectiveKind = diary.mineralLike() ? RecipeKind.MINERAL : RecipeKind.ORGANIC;
                excludeDiary = diary.id();
            }
            return readModel.recommendations(viewerId, effectiveKind, excludeDiary, size);
        });
    }
}
