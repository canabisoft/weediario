package br.com.weediario.identity.infrastructure.mail;

import br.com.weediario.identity.application.port.AccountMailer;
import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.SecurityEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Manda os e-mails da conta por SMTP ({@code spring.mail.*}), numa fila própria em segundo plano: a resposta HTTP não
 * espera o provedor (o tempo não revela se a conta tem e-mail) e uma falha de envio não desfaz a operação.
 * <p>
 * Sem SMTP configurado, o envio fica desligado e só se registra que uma mensagem deixou de sair (sem o endereço).
 * Assuntos neutros: quem vê a caixa de entrada por cima do ombro não lê "cannabis" nem o nome do app.
 */
@Component
public class SpringAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(SpringAccountMailer.class);
    private static final int QUEUE_CAPACITY = 500;

    private final JavaMailSender sender;
    private final AccountMailProperties properties;
    private final ThreadPoolExecutor queue = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "account-mailer");
                thread.setDaemon(true);
                return thread;
            }, (runnable, executor) -> log.warn("Fila de e-mails cheia: uma mensagem da conta foi descartada"));

    public SpringAccountMailer(ObjectProvider<JavaMailSender> sender, AccountMailProperties properties) {
        this.properties = properties;
        this.sender = properties.from().isEmpty() ? null : sender.getIfAvailable();
        if (this.sender == null) {
            log.warn("Envio de e-mail desligado (configure spring.mail.host e weediario.identity.mail.from): "
                    + "confirmação de e-mail e redefinição de senha por e-mail não vão chegar");
        }
    }

    @Override
    public void sendEmailVerification(EmailAddress to, String username, String token) {
        String link = properties.publicUrl() + "/confirmar-email?token=" + token;
        send(to, "Confirme seu e-mail", """
                Olá, %s.

                Confirme que este e-mail é seu para poder usá-lo na recuperação da senha:
                %s

                O link vale por 24 horas. Se não foi você que cadastrou este endereço, ignore esta mensagem: \
                sem a confirmação ele não é usado para nada.
                """.formatted(username, link), link);
    }

    @Override
    public void sendPasswordReset(EmailAddress to, String username, String token) {
        String link = properties.publicUrl() + "/redefinir-senha?token=" + token;
        send(to, "Redefinição de senha", """
                Olá, %s.

                Recebemos um pedido para redefinir a sua senha. Para escolher uma nova, abra:
                %s

                O link vale por 30 minutos e funciona uma vez só. Se não foi você, ignore esta mensagem: \
                sua senha continua a mesma.
                """.formatted(username, link), link);
    }

    @Override
    public void notifySecurityEvent(EmailAddress to, String username, SecurityEvent.Kind kind) {
        send(to, "Aviso de segurança da sua conta", """
                Olá, %s.

                %s agora há pouco.

                Se foi você, está tudo certo. Se não foi, recupere a conta com a frase de 12 palavras \
                em %s/recuperar — isso troca a senha e encerra todas as sessões abertas.
                """.formatted(username, kind.description(), properties.publicUrl()), null);
    }

    @Override
    public void notifyAccountDeleted(EmailAddress to, String username) {
        send(to, "Sua conta foi apagada", """
                Olá, %s.

                A sua conta foi apagada agora há pouco, junto com diários, fotos, receitas e o resto do que era seu.
                Este endereço também foi apagado: esta é a última mensagem que você recebe de nós.

                Se não foi você, alguém tinha a sua senha. Não há como desfazer a exclusão; troque essa senha nos
                outros lugares onde a usava.
                """.formatted(username), null);
    }

    private void send(EmailAddress to, String subject, String body, String link) {
        if (sender == null) {
            if (properties.logLinks() && link != null) {
                log.info("[dev] e-mail desligado; link que seria enviado: {}", link);
            } else {
                log.info("E-mail desligado: mensagem \"{}\" não enviada", subject);
            }
            return;
        }
        queue.execute(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.from());
                message.setTo(to.value());
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
            } catch (RuntimeException e) {
                // Sem o endereço no log.
                log.warn("Falha ao enviar e-mail \"{}\": {}", subject, e.getClass().getSimpleName());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        queue.shutdown();
    }
}
