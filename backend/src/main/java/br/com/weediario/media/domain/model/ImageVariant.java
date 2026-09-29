package br.com.weediario.media.domain.model;

public enum ImageVariant {
    /** Até 2048px no maior lado: visualização em tela cheia. */
    FULL,
    /** Até 480px no maior lado: cards do feed e da linha do tempo. */
    THUMBNAIL
}
