package br.com.weediario.media.domain.model;

public enum ImageStatus {
    /** Enviada, mas ainda não usada em nenhum update. É apagada se ficar órfã. */
    PENDING,
    /** Vinculada a um update de diário. */
    ATTACHED
}
