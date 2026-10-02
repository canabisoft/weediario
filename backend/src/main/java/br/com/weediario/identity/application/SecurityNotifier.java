package br.com.weediario.identity.application;

import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.domain.model.AccountEmail;
import br.com.weediario.identity.domain.model.SecurityEvent;
import br.com.weediario.identity.domain.model.User;
import br.com.weediario.identity.domain.repository.AccountEmailRepository;
import br.com.weediario.identity.domain.repository.SecurityEventRepository;
import br.com.weediario.shared.application.UnitOfWork;

import java.time.Clock;
import java.util.Optional;

/**
 * Registra uma mudança sensível na conta (para o dono ver no perfil) e avisa por e-mail, se a conta tiver um e-mail
 * confirmado. Chamado depois que a mudança foi gravada.
 */
public final class SecurityNotifier {

    private final SecurityEventRepository events;
    private final AccountEmailRepository emails;
    private final AccountMailer mailer;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public SecurityNotifier(SecurityEventRepository events, AccountEmailRepository emails, AccountMailer mailer,
                            UnitOfWork unitOfWork, Clock clock) {
        this.events = events;
        this.emails = emails;
        this.mailer = mailer;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public void recorded(User user, SecurityEvent.Kind kind) {
        notify(user, kind, Optional.empty());
    }

    /**
     * @param previousEmail endereço confirmado de antes da mudança (troca ou remoção de e-mail): avisamos o endereço
     *                      antigo, que é quem precisa saber se foi um invasor que trocou
     */
    public void notify(User user, SecurityEvent.Kind kind, Optional<AccountEmail> previousEmail) {
        Optional<AccountEmail> current = unitOfWork.execute(() -> {
            events.record(new SecurityEvent(user.id(), kind, clock.instant()));
            return emails.find(user.id());
        });
        previousEmail.or(() -> current)
                .filter(AccountEmail::isVerified)
                .ifPresent(email -> mailer.notifySecurityEvent(email.address(), user.username().value(), kind));
    }
}
