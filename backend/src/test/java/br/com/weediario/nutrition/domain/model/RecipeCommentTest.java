package br.com.weediario.nutrition.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeCommentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID ASKER = UUID.randomUUID();

    @Test
    void repliesHaveASingleLevel() {
        Recipe recipe = recipe(RecipeVisibility.PUBLIC);
        RecipeComment root = RecipeComment.post(recipe, ASKER, "Funciona em coco?", CLOCK);
        RecipeComment reply = RecipeComment.reply(recipe, root, AUTHOR, "Funciona", CLOCK);
        RecipeComment replyToReply = RecipeComment.reply(recipe, reply, ASKER, "Obrigado", CLOCK);

        assertThat(root.parentId()).isEmpty();
        assertThat(reply.parentId()).contains(root.id());
        assertThat(replyToReply.parentId()).contains(root.id());
        assertThat(replyToReply.rootId()).isEqualTo(root.id());
    }

    @Test
    void repliesFollowTheSameRulesAsComments() {
        Recipe recipe = recipe(RecipeVisibility.PUBLIC);
        RecipeComment root = RecipeComment.post(recipe, ASKER, "Funciona em coco?", CLOCK);

        assertThatThrownBy(() -> RecipeComment.reply(recipe(RecipeVisibility.PUBLIC), root, AUTHOR, "oi", CLOCK))
                .as("comentário de outra receita").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> RecipeComment.reply(recipe, root, AUTHOR, " ", CLOCK))
                .isInstanceOf(DomainException.class);

        RecipeComment reply = RecipeComment.reply(recipe, root, AUTHOR, "Funciona", CLOCK);
        assertThat(reply.canBeDeletedBy(AUTHOR, recipe, false)).isTrue();
        assertThat(reply.canBeDeletedBy(ASKER, recipe, false)).as("dono da receita é o AUTHOR, não o ASKER").isFalse();
        reply.holdForModeration();
        assertThat(reply.isVisibleTo(AUTHOR)).isTrue();
        assertThat(reply.isVisibleTo(ASKER)).isFalse();
    }

    private static Recipe recipe(RecipeVisibility visibility) {
        RecipeDetails details = new RecipeDetails("Base", RecipeKind.MINERAL, RecipeApplication.FERTIGATION, null,
                BigDecimal.TEN, null, null, null,
                List.of(Ingredient.generic("MKP", null, BigDecimal.ONE, IngredientUnit.G)));
        return Recipe.create(AUTHOR, details, visibility, CLOCK);
    }
}
