package br.com.weediario.catalog.application;

public class ModeratorOnlyException extends RuntimeException {

    public ModeratorOnlyException() {
        super("Ação exclusiva da moderação");
    }
}
