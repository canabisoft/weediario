package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.application.port.ModeratedAccounts.Account;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationAction;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ForbiddenActionException;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Gestão da equipe, exclusiva de admin: ver quem é moderador ou admin, promover um grower a moderador e tirar um
 * moderador da equipe. Cada mudança vai para o log de moderação.
 * <p>
 * A regra é conferida aqui, além da rota: qualquer outro papel recebe 403. Ninguém muda o próprio papel, e admins não
 * são criados nem removidos pela API (só no banco), para que uma falha aqui nunca crie um admin nem deixe o app sem
 * nenhum.
 */
public final class ManageTeamUseCase {

    private final ModeratedAccounts accounts;
    private final ModerationRepository repository;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageTeamUseCase(ModeratedAccounts accounts, ModerationRepository repository, UnitOfWork unitOfWork,
                             Clock clock) {
        this.accounts = accounts;
        this.repository = repository;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<TeamMember> team(boolean admin) {
        requireAdmin(admin);
        return unitOfWork.execute(() -> accounts.staff().stream()
                .map(account -> new TeamMember(account.username(), account.role(), account.status()))
                .toList());
    }

    public TeamMember promote(UUID adminId, boolean admin, String username, String note) {
        requireAdmin(admin);
        return unitOfWork.execute(() -> {
            Account target = target(adminId, username);
            accounts.promoteToModerator(target.id());
            log(target, adminId, ModerationAction.ROLE_GRANTED, note);
            return new TeamMember(target.username(), "MODERATOR", target.status());
        });
    }

    public void removeFromTeam(UUID adminId, boolean admin, String username, String note) {
        requireAdmin(admin);
        unitOfWork.run(() -> {
            Account target = target(adminId, username);
            if (target.admin()) {
                throw new ForbiddenActionException("Admins não são removidos pelo app");
            }
            accounts.removeFromTeam(target.id());
            log(target, adminId, ModerationAction.ROLE_REVOKED, note);
        });
    }

    private Account target(UUID adminId, String username) {
        Account target = accounts.findByUsername(username == null ? "" : username.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Grower"));
        if (target.id().equals(adminId)) {
            throw new ForbiddenActionException("Você não pode mudar o próprio papel");
        }
        return target;
    }

    private void log(Account target, UUID adminId, ModerationAction action, String note) {
        repository.log(ModerationLogEntry.of(new ContentRef(TargetType.PROFILE, target.id()), target.id(),
                target.username(), adminId, action, ModerationReason.STAFF_CHANGE, note, null, clock.instant()));
    }

    private static void requireAdmin(boolean admin) {
        if (!admin) {
            throw new ForbiddenActionException("Só admins gerenciam a equipe");
        }
    }

    /** @param role MODERATOR ou ADMIN; @param status ACTIVE, SUSPENDED ou BANNED */
    public record TeamMember(String username, String role, String status) {
    }
}
