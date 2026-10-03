package br.com.weediario.identity.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Ao subir, confere se o SMTP aceita conexão e login, e diz no log se o envio de e-mail está pronto. Em produção é a
 * primeira coisa a olhar: "E-mail: SMTP OK". Não envia nada e não registra endereço, usuário nem senha.
 */
@Component
public class MailStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(MailStartupCheck.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AccountMailProperties properties;

    public MailStartupCheck(ObjectProvider<JavaMailSender> sender, AccountMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null || properties.from().isEmpty()) {
            log.warn("E-mail: DESLIGADO (falta SPRING_MAIL_HOST ou WEEDIARIO_MAIL_FROM)");
            return;
        }
        if (!(mail instanceof JavaMailSenderImpl smtp)) {
            return;
        }
        try {
            smtp.testConnection();
            log.info("E-mail: SMTP OK ({}:{}), links apontando para {}", smtp.getHost(), smtp.getPort(),
                    properties.publicUrl());
        } catch (Exception e) {
            // Só a classe do erro: a mensagem do provedor pode trazer o usuário da conta SMTP.
            log.error("E-mail: SMTP FALHOU ({}:{}): {}. Confira SPRING_MAIL_* no .env", smtp.getHost(), smtp.getPort(),
                    e.getClass().getSimpleName());
        }
    }
}
