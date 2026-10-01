package br.com.weediario.notification.domain.model;

/**
 * O que aconteceu. Reações e novos seguidores são <strong>anônimos</strong>: a notificação nunca diz quem foi,
 * do mesmo jeito que o resto do app só mostra contagens.
 */
public enum NotificationType {

    /** Alguém comentou no seu diário (o apelido de quem comentou já é público no comentário). */
    COMMENT(false, false),
    /** Seu diário recebeu reações. */
    REACTION(true, true),
    /** Você ganhou seguidores (a lista de seguidores nunca é exposta, nem para o dono do perfil). */
    NEW_FOLLOWER(true, true),
    /** Um grower que você segue publicou um update num diário público. */
    FOLLOWED_UPDATE(false, true),
    /** Alguém respondeu um comentário seu (em diário ou receita); o apelido de quem respondeu já é público. */
    REPLY(false, false);

    private final boolean anonymous;
    private final boolean aggregated;

    NotificationType(boolean anonymous, boolean aggregated) {
        this.anonymous = anonymous;
        this.aggregated = aggregated;
    }

    /** Não guarda quem fez a ação. */
    public boolean anonymous() {
        return anonymous;
    }

    /** Enquanto não lida, novas ocorrências somam no contador em vez de criar outra notificação. */
    public boolean aggregated() {
        return aggregated;
    }
}
