package br.com.weediario.moderation.application.usecase;

import br.com.weediario.moderation.application.port.ModeratedAccounts;
import br.com.weediario.moderation.application.port.ModeratedAccounts.Account;
import br.com.weediario.moderation.application.port.ModeratedContent;
import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;
import br.com.weediario.moderation.domain.model.ContentRef;
import br.com.weediario.moderation.domain.model.ModerationLogEntry;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.AccountAction;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationAction;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.ModerationReason;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.TargetType;
import br.com.weediario.moderation.domain.model.ModerationVocabulary.Verdict;
import br.com.weediario.moderation.domain.repository.ModerationRepository;
import br.com.weediario.shared.application.ForbiddenActionException;
import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Decisões da moderação, todas registradas no log de moderação: manter (denúncia improcedente) ou remover o
 * conteúdo e, junto com a remoção, suspender ou banir quem publicou.
 * <p>
 * Hierarquia: contas da equipe (moderador ou admin) nunca são suspensas ou banidas por aqui, só um admin remove
 * conteúdo publicado por alguém da equipe, ninguém decide sobre o próprio conteúdo e só admin desfaz banimento.
 * Tentativas fora disso dão 403.
 */
public final class ModerateContentUseCase {

    private final ModerationRepository repository;
    private final ModeratedContent content;
    private final ModeratedAccounts accounts;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ModerateContentUseCase(ModerationRepository repository, ModeratedContent content, ModeratedAccounts accounts,
                                  UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.content = content;
        this.accounts = accounts;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** @param admin quem decide é admin (pode remover conteúdo de alguém da equipe) */
    public void decide(UUID moderatorId, boolean moderator, boolean admin, Decision decision) {
        requireModerator(moderator);
        if (decision.reason() == ModerationReason.STAFF_CHANGE) {
            throw new DomainException("Motivo inválido para uma decisão de conteúdo");
        }
        ContentRef ref = new ContentRef(decision.type(), decision.targetId());
        unitOfWork.run(() -> {
            ContentInfo info = content.find(ref).orElseThrow(() -> new ResourceNotFoundException("Conteúdo"));
            if (moderatorId.equals(info.authorId())) {
                // Conflito de interesse: liberar o próprio conteúdo retido seria burlar o filtro.
                throw new ForbiddenActionException("Você não decide sobre o próprio conteúdo; outra pessoa da equipe decide");
            }
            Instant now = clock.instant();
            if (decision.verdict() == Verdict.REMOVE) {
                requireAllowedOver(info.authorId(), admin, decision.account());
            }
            if (decision.verdict() == Verdict.KEEP) {
                if (decision.account() != AccountAction.NONE) {
                    throw new DomainException("Para suspender ou banir, remova o conteúdo");
                }
                content.restore(ref);
                repository.log(ModerationLogEntry.of(ref, info.authorId(), info.author(), moderatorId,
                        ModerationAction.CONTENT_RESTORED, ModerationReason.UNFOUNDED, decision.note(), null, now));
            } else {
                if (decision.reason() == null || decision.reason() == ModerationReason.UNFOUNDED) {
                    throw new DomainException("Informe o motivo da remoção");
                }
                String hash = sha256(info.text());
                content.remove(ref);
                repository.log(ModerationLogEntry.of(ref, info.authorId(), info.author(), moderatorId,
                        ModerationAction.CONTENT_REMOVED, decision.reason(), decision.note(), hash, now));
                if (decision.account() != AccountAction.NONE) {
                    actOnAccount(moderatorId, info, decision, now);
                }
            }
            repository.release(ref);
            repository.clearReports(ref);
        });
    }

    /**
     * Reativa uma conta suspensa ou banida (erro de moderação, recurso aceito). Banimento só é desfeito por admin:
     * uma conta de moderação comprometida não consegue devolver o acesso a quem foi banido.
     */
    public void reinstate(UUID moderatorId, boolean moderator, boolean admin, String username, String note) {
        requireModerator(moderator);
        unitOfWork.run(() -> {
            Account account = accounts.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("Grower"));
            if ("ACTIVE".equals(account.status())) {
                throw new DomainException("A conta já está ativa");
            }
            if ("BANNED".equals(account.status()) && !admin) {
                throw new ForbiddenActionException("Só um admin desfaz banimento");
            }
            if (account.id().equals(moderatorId)) {
                throw new ForbiddenActionException("Você não reativa a própria conta");
            }
            accounts.reinstate(account.id());
            repository.log(ModerationLogEntry.of(new ContentRef(TargetType.PROFILE, account.id()), account.id(),
                    account.username(), moderatorId, ModerationAction.USER_REINSTATED, ModerationReason.UNFOUNDED, note,
                    null, clock.instant()));
        });
    }

    /** Checado antes de mexer em qualquer coisa: a equipe não é moderada por quem está abaixo (ou ao lado) dela. */
    private void requireAllowedOver(UUID authorId, boolean admin, AccountAction accountAction) {
        Optional<Account> author = authorId == null ? Optional.empty() : accounts.find(authorId);
        if (author.isEmpty() || !author.get().staff()) {
            return;
        }
        if (accountAction != AccountAction.NONE) {
            throw new ForbiddenActionException(
                    "Contas da equipe não são suspensas nem banidas. Um admin precisa tirar a conta da equipe antes.");
        }
        if (!admin) {
            throw new ForbiddenActionException("Só um admin remove conteúdo publicado por alguém da equipe");
        }
    }

    private void actOnAccount(UUID moderatorId, ContentInfo info, Decision decision, Instant now) {
        Account author = accounts.find(info.authorId()).orElseThrow(() -> new ResourceNotFoundException("Grower"));
        ModerationAction action;
        if (decision.account() == AccountAction.BAN) {
            accounts.ban(author.id());
            action = ModerationAction.USER_BANNED;
        } else {
            accounts.suspend(author.id());
            action = ModerationAction.USER_SUSPENDED;
        }
        repository.log(ModerationLogEntry.of(new ContentRef(TargetType.PROFILE, author.id()), author.id(),
                author.username(), moderatorId, action, decision.reason(), decision.note(), null, now));
    }

    static String sha256(String text) {
        if (text == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireModerator(boolean moderator) {
        if (!moderator) {
            throw new ModeratorOnlyException();
        }
    }

    /**
     * @param reason obrigatório para remover; ignorado ao manter (vira "improcedente")
     * @param note   observação curta da moderação (fica no log)
     */
    public record Decision(TargetType type, UUID targetId, Verdict verdict, AccountAction account,
                           ModerationReason reason, String note) {

        public Decision {
            if (type == null || targetId == null || verdict == null) {
                throw new DomainException("Decisão incompleta");
            }
            account = account == null ? AccountAction.NONE : account;
        }
    }
}
