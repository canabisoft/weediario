package br.com.weediario.social.application.query;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura sobre growers: ranking e perfil público.
 * <p>
 * Só entram diários públicos nas estatísticas, para que números públicos não revelem a existência de
 * diários privados. Listas de seguidores nunca são expostas, só a contagem.
 */
public interface GrowerDirectory {

    PageResult<RankedGrower> ranking(RankingSort sort, String usernameQuery, PageQuery page);

    Optional<GrowerProfile> profile(String username, Optional<UUID> viewerId);

    List<FollowedGrower> followedBy(UUID followerId);

    enum RankingSort {
        DIARIES,
        LIKES,
        GRAMS_PER_PLANT
    }

    record RankedGrower(long position, String username, boolean hasAvatar, long diaries, long likes,
                        BigDecimal gramsPerPlant) {
    }

    /**
     * @param followedByViewer se quem está vendo segue este grower (falso para visitantes)
     * @param isViewer         se é o próprio perfil de quem está vendo
     */
    record GrowerProfile(String username, String bio, boolean hasAvatar, long diaries, long likes,
                         BigDecimal gramsPerPlant, long followers, boolean followedByViewer, boolean isViewer) {
    }

    record FollowedGrower(String username, boolean hasAvatar) {
    }
}
