package br.com.weediario.nutrition.infrastructure.social;

import br.com.weediario.nutrition.application.port.CommentReactorPseudonyms;
import br.com.weediario.social.application.port.Pseudonymizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Reusa o HMAC das reações do contexto social, com um propósito próprio para comentários de receita. */
@Component
public class SocialCommentReactorPseudonyms implements CommentReactorPseudonyms {

    static final String PURPOSE = "recipe-comment-reaction";

    private final Pseudonymizer pseudonymizer;

    public SocialCommentReactorPseudonyms(Pseudonymizer pseudonymizer) {
        this.pseudonymizer = pseudonymizer;
    }

    @Override
    public String of(UUID userId, UUID commentId) {
        return pseudonymizer.pseudonymOf(userId, commentId, PURPOSE).value();
    }
}
