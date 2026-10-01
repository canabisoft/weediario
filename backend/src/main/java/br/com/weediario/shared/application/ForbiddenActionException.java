package br.com.weediario.shared.application;

/**
 * Ação proibida para o papel de quem pede (403), com o motivo: por exemplo, um moderador tentando agir sobre outra
 * conta da equipe, ou alguém sem papel de admin chamando a gestão da equipe.
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
