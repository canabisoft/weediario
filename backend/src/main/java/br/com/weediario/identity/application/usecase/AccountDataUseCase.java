package br.com.weediario.identity.application.usecase;

import br.com.weediario.identity.application.CredentialCheck;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.Role;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.model.UserId;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.SecurityEventRepository;
import br.com.weediario.identity.domain.repository.UserRepository;
import br.com.weediario.shared.application.ForbiddenActionException;
import br.com.weediario.shared.application.PersonalDataSection;
import br.com.weediario.shared.application.UnitOfWork;
import br.com.weediario.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Direitos do titular (LGPD, art. 18): baixar todos os dados da conta e apagar a conta.
 * <p>
 * Os dois exigem a senha atual, mesmo com a sessão aberta: com só um token roubado, alguém levaria tudo ou apagaria a
 * conta do dono. Cada contexto entrega a sua parte por {@link PersonalDataSection}.
 */
public final class AccountDataUseCase {

    private final UserRepository users;
    private final AccountEmailRepository emails;
    private final SecurityEventRepository events;
    private final List<PersonalDataSection> sections;
    private final CredentialCheck credentials;
    private final AccountMailer mailer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AccountDataUseCase(UserRepository users, AccountEmailRepository emails, SecurityEventRepository events,
                              List<PersonalDataSection> sections, CredentialCheck credentials, AccountMailer mailer,
                              UnitOfWork unitOfWork, Clock clock) {
        this.users = users;
        this.emails = emails;
        this.events = events;
        this.sections = List.copyOf(sections);
        this.credentials = credentials;
        this.mailer = mailer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /** Confere a senha antes de montar o pacote (que é escrito depois, aos poucos, por quem chamou). */
    public Export export(UUID userId, String currentPassword) {
        User user = load(userId);
        credentials.require(user, currentPassword);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gerado_em", clock.instant().toString());
        data.put("conta", unitOfWork.execute(() -> account(user)));
        for (PersonalDataSection section : sections) {
            Object part = unitOfWork.execute(() -> section.export(userId));
            if (part != null) {
                data.put(section.name(), part);
            }
        }
        return new Export(user.username().value(), data, sink -> sections.forEach(s -> s.writeFiles(userId, sink)));
    }

    /**
     * Apaga a conta e tudo o que é dela. Pede a senha e o apelido digitado (para não apagar sem querer). Admins não se
     * apagam pelo app: a equipe não pode ficar sem admin por um clique.
     */
    public void delete(UUID userId, String currentPassword, String typedUsername) {
        User user = load(userId);
        if (user.role() == Role.ADMIN) {
            throw new ForbiddenActionException("Contas de admin são removidas fora do app");
        }
        credentials.require(user, currentPassword);
        if (typedUsername == null || !typedUsername.strip().toLowerCase(Locale.ROOT)
                .equals(user.username().value().toLowerCase(Locale.ROOT))) {
            throw new DomainException("Digite o seu apelido para confirmar");
        }

        Optional<AccountEmail> email = unitOfWork.execute(() -> emails.find(user.id()));
        List<Runnable> afterCommit = unitOfWork.execute(() -> {
            List<Runnable> cleanups = new ArrayList<>();
            sections.forEach(section -> cleanups.add(section.prepareErasure(userId)));
            users.delete(user.id());
            return cleanups;
        });
        // Fora do banco (arquivos): uma falha aqui não traz a conta de volta; o que sobrar, a faxina periódica pega.
        afterCommit.forEach(cleanup -> {
            try {
                cleanup.run();
            } catch (RuntimeException ignored) {
                // Segue para as próximas: apagar o máximo possível.
            }
        });
        email.filter(AccountEmail::isVerified)
                .ifPresent(found -> mailer.notifyAccountDeleted(found.address(), user.username().value()));
    }

    private Map<String, Object> account(User user) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("apelido", user.username().value());
        account.put("bio", user.bio().orElse(null));
        account.put("tem_foto_de_perfil", user.avatarImageId().isPresent());
        account.put("papel", user.role().name());
        account.put("situacao", user.status().name());
        account.put("criada_em", user.createdAt().toString());
        account.put("termos_versao", user.termsVersion().orElse(null));
        account.put("termos_aceitos_em", user.termsAcceptedAt().map(Instant::toString).orElse(null));
        account.put("tem_senha", user.hasPassword());
        account.put("tem_frase_de_recuperacao", user.hasRecovery());
        Optional<AccountEmail> email = emails.find(user.id());
        account.put("email", email.map(found -> found.address().value()).orElse(null));
        account.put("email_confirmado_em", email.flatMap(AccountEmail::verifiedAt).map(Instant::toString).orElse(null));
        account.put("historico_de_seguranca", events.since(user.id(),
                        clock.instant().minus(Duration.ofDays(SecurityEvent.RETENTION_DAYS))).stream()
                .map(event -> Map.of("o_que", event.kind().description(), "quando", event.occurredAt().toString()))
                .toList());
        return account;
    }

    private User load(UUID userId) {
        return unitOfWork.execute(() -> users.findById(new UserId(userId)))
                .orElseThrow(InvalidCredentialsException::new);
    }

    /**
     * @param data  conteúdo do {@code dados.json}
     * @param files escreve os arquivos (fotos) no pacote, um por vez
     */
    public record Export(String username, Map<String, Object> data, FileWriter files) {
    }

    @FunctionalInterface
    public interface FileWriter {
        void writeTo(PersonalDataSection.FileSink sink);
    }
}
