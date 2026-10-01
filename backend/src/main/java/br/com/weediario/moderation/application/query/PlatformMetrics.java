package br.com.weediario.moderation.application.query;

import java.time.Instant;

/**
 * Números da plataforma para o painel do admin, calculados por contagem no banco a cada pedido. Sem rastreamento:
 * nada por pessoa, nenhum cookie ou ferramenta externa, e nenhum dado do acompanhamento de tratamento (dado de saúde
 * fica fora até das contagens).
 */
public interface PlatformMetrics {

    Accounts accounts();

    Content content();

    /** Atividade desde o instante informado (ex.: últimos 7 ou 30 dias). */
    Activity activitySince(Instant since);

    long moderationDecisionsSince(Instant since);

    /** @param active contas que podem entrar (nem suspensas nem banidas) */
    record Accounts(long total, long active, long suspended, long banned, long moderators, long admins) {
    }

    /**
     * Conteúdo visível ao público (sem o que está em análise ou foi removido); diários privados à parte.
     *
     * @param diaryUpdates updates de diários públicos
     */
    record Content(long publicDiaries, long privateDiaries, long diaryUpdates, long comments, long publicRecipes,
                   long recipeComments, long reviews) {

        public long published() {
            return publicDiaries + diaryUpdates + comments + publicRecipes + recipeComments + reviews;
        }
    }

    /**
     * @param activeGrowers contas distintas que publicaram algo no período (diário, update, comentário, avaliação ou
     *                      receita); só o número, nunca quem
     */
    record Activity(long newAccounts, long newDiaries, long diaryUpdates, long comments, long reviews, long recipes,
                    long activeGrowers) {
    }
}
