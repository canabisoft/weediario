package br.com.weediario.identity.application.exception;

/**
 * Não revela se o e-mail já está cadastrado.
 */
public class RegistrationRejectedException extends RuntimeException {

    public RegistrationRejectedException() {
        super("Não foi possível concluir o cadastro com os dados informados");
    }
}
