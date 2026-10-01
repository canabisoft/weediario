package br.com.weediario.nutrition.domain.model;

/**
 * Reações anônimas nos comentários das receitas (as mesmas das reações de diário). O emoji é responsabilidade do
 * frontend; a API trafega só o código.
 */
public enum CommentReaction {
    /** 🔥 */
    FIRE,
    /** 🌿 */
    HERB,
    /** 👏 */
    CLAP
}
