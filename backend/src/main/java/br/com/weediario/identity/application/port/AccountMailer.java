package br.com.weediario.identity.application.port;

import br.com.weediario.identity.domain.model.EmailAddress;
import br.com.weediario.identity.domain.model.SecurityEvent;

/**
 * Envio dos e-mails da conta. A implementação manda em segundo plano: a resposta HTTP leva o mesmo tempo haja e-mail
 * ou não, e uma falha no provedor não derruba a operação. As mensagens nunca citam cannabis no assunto.
 */
public interface AccountMailer {

    /** @param token valor bruto do token, que vai só no link */
    void sendEmailVerification(EmailAddress to, String username, String token);

    /** @param token valor bruto do token, que vai só no link */
    void sendPasswordReset(EmailAddress to, String username, String token);

    void notifySecurityEvent(EmailAddress to, String username, SecurityEvent.Kind kind);

    /** Última mensagem para o endereço: a conta foi apagada (se não foi a pessoa, ela fica sabendo). */
    void notifyAccountDeleted(EmailAddress to, String username);
}
