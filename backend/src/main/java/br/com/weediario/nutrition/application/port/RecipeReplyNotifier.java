package br.com.weediario.nutrition.application.port;

import java.util.UUID;

/**
 * Avisa o autor de um comentário de receita que ele foi respondido. Quem respondeu é passado para não avisar
 * ninguém da resposta ao próprio comentário (o apelido dele já é público na resposta).
 */
public interface RecipeReplyNotifier {

    void replied(UUID recipeId, UUID answeredAuthorId, UUID replierId);
}
