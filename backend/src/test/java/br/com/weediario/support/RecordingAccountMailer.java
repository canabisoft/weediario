package br.com.weediario.support;

import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.application.port.BackgroundTasks;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.SecurityEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Guarda as mensagens em vez de enviar, para os testes lerem os links (síncrono, sem SMTP). */
public class RecordingAccountMailer implements AccountMailer {

    public enum Kind { VERIFY_EMAIL, RESET_PASSWORD, SECURITY_EVENT, ACCOUNT_DELETED }

    public record Sent(Kind kind, String to, String username, String token, SecurityEvent.Kind event) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmailVerification(EmailAddress to, String username, String token) {
        sent.add(new Sent(Kind.VERIFY_EMAIL, to.value(), username, token, null));
    }

    @Override
    public void sendPasswordReset(EmailAddress to, String username, String token) {
        sent.add(new Sent(Kind.RESET_PASSWORD, to.value(), username, token, null));
    }

    @Override
    public void notifySecurityEvent(EmailAddress to, String username, SecurityEvent.Kind kind) {
        sent.add(new Sent(Kind.SECURITY_EVENT, to.value(), username, null, kind));
    }

    @Override
    public void notifyAccountDeleted(EmailAddress to, String username) {
        sent.add(new Sent(Kind.ACCOUNT_DELETED, to.value(), username, null, null));
    }

    /** Última mensagem desse tipo para o apelido (o contexto é compartilhado entre testes). */
    public Optional<Sent> last(Kind kind, String username) {
        return sent.stream().filter(m -> m.kind() == kind && m.username().equalsIgnoreCase(username))
                .reduce((first, second) -> second);
    }

    public List<Sent> to(String username) {
        return sent.stream().filter(m -> m.username().equalsIgnoreCase(username)).toList();
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        RecordingAccountMailer recordingAccountMailer() {
            return new RecordingAccountMailer();
        }

        /** Nos testes, o "segundo plano" roda na hora: o teste lê o e-mail logo depois da requisição. */
        @Bean
        @Primary
        BackgroundTasks immediateBackgroundTasks() {
            return Runnable::run;
        }
    }
}
