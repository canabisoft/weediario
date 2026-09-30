package br.com.weediario.shared.application;

public class ModeratorOnlyException extends RuntimeException {

    public ModeratorOnlyException() {
        super("Ação exclusiva da moderação");
    }
}
