package br.com.weediario.diary.application.exception;

public class GrowerNotAllowedException extends RuntimeException {

    public GrowerNotAllowedException() {
        super("Conta sem permissão para publicar diários");
    }
}
