package br.com.weediario.social.application.usecase;

import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.social.application.port.GrowerLookup;
import br.com.weediario.social.application.port.InteractionNotifier;
import br.com.weediario.social.application.query.GrowerDirectory;
import br.com.weediario.social.application.query.GrowerDirectory.FollowedGrower;
import br.com.weediario.social.application.query.GrowerDirectory.GrowerProfile;
import br.com.weediario.social.application.query.GrowerDirectory.RankedGrower;
import br.com.weediario.social.application.query.GrowerDirectory.RankingSort;
import br.com.weediario.social.domain.repository.FollowRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranking, perfil público e "seguir" growers.
 */
public final class GrowersUseCase {

    public static final int MAX_FOLLOWING = 1000;

    private final GrowerDirectory directory;
    private final GrowerLookup growers;
    private final FollowRepository follows;
    private final InteractionNotifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public GrowersUseCase(GrowerDirectory directory, GrowerLookup growers, FollowRepository follows,
                          InteractionNotifier notifier, UnitOfWork unitOfWork, Clock clock) {
        this.directory = directory;
        this.growers = growers;
        this.follows = follows;
        this.notifier = notifier;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public PageResult<RankedGrower> ranking(RankingSort sort, String query, PageQuery page) {
        String normalized = query == null || query.isBlank() ? null : query.strip();
        return directory.ranking(sort == null ? RankingSort.DIARIES : sort, normalized, page);
    }

    public GrowerProfile profile(String username, Optional<UUID> viewerId) {
        return directory.profile(username, viewerId).orElseThrow(() -> new ResourceNotFoundException("Grower"));
    }

    /** Só o próprio usuário vê quem ele segue. */
    public List<FollowedGrower> following(UUID followerId) {
        return directory.followedBy(followerId);
    }

    public void follow(UUID followerId, String username) {
        unitOfWork.run(() -> {
            UUID followee = growerId(username);
            if (followee.equals(followerId)) {
                throw new DomainException("Não é possível seguir a si mesmo");
            }
            if (follows.countFollowing(followerId) >= MAX_FOLLOWING) {
                throw new DomainException("Limite de " + MAX_FOLLOWING + " growers seguidos atingido");
            }
            if (follows.follow(followerId, followee, clock.instant())) {
                notifier.followed(followee, followerId);
            }
        });
    }

    public void unfollow(UUID followerId, String username) {
        unitOfWork.run(() -> follows.unfollow(followerId, growerId(username)));
    }

    private UUID growerId(String username) {
        return growers.activeGrowerId(username).orElseThrow(() -> new ResourceNotFoundException("Grower"));
    }
}
