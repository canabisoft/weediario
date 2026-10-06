package br.com.weediario.shared.application;

import br.com.weediario.shared.application.ActionQuota.Limit;

import java.time.Duration;

/**
 * Todas as cotas por conta num lugar só, para revisar de uma vez. Os tetos ficam bem acima do uso normal de um grower
 * (ninguém publica 60 updates num dia): o alvo é o abuso em série, não quem usa muito.
 */
public final class ActionLimits {

    /** Denúncias (de qualquer conteúdo ou perfil). */
    public static final Limit REPORTS = new Limit("report", 10, Duration.ofDays(1));
    /** Baixar os dados da conta: monta um ZIP com todas as fotos, o pedido mais caro do app. */
    public static final Limit DATA_EXPORTS = new Limit("data-export", 3, Duration.ofDays(1));
    /** Cadastrar ou trocar o e-mail: cada troca manda um e-mail para um endereço escolhido por quem pede. */
    public static final Limit EMAIL_CHANGES = new Limit("email-change", 5, Duration.ofDays(1));
    /** Seguir alguém (cada follow notifica a pessoa seguida). */
    public static final Limit FOLLOWS = new Limit("follow", 100, Duration.ofHours(1));
    /** Reações em diários e comentários e notas de receitas, somadas (as reações em diário notificam o dono). */
    public static final Limit REACTIONS = new Limit("reaction", 300, Duration.ofHours(1));
    /** Receitas criadas ou replicadas. */
    public static final Limit RECIPES = new Limit("recipe", 20, Duration.ofDays(1));
    /** Diários abertos. */
    public static final Limit DIARIES = new Limit("diary", 30, Duration.ofDays(1));
    /** Updates publicados nos diários. */
    public static final Limit DIARY_UPDATES = new Limit("diary-update", 60, Duration.ofDays(1));
    /** Fotos enviadas (cada uma é decodificada e recodificada no servidor). */
    public static final Limit IMAGE_UPLOADS = new Limit("image-upload", 200, Duration.ofDays(1));

    private ActionLimits() {
    }
}
