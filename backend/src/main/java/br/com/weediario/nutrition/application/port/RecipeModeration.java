package br.com.weediario.nutrition.application.port;

import java.util.UUID;

/**
 * Manda para a fila da moderação o que o filtro anti-comércio reteve (a receita já fica privada e travada; o
 * comentário, oculto).
 */
public interface RecipeModeration {

    void recipeHeld(UUID recipeId, String reason);

    void commentHeld(UUID commentId, String reason);
}
