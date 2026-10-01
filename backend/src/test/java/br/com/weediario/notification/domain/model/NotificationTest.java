package br.com.weediario.notification.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void replyNotifiesTheAnsweredAuthorButNeverYourself() {
        UUID author = UUID.randomUUID();
        UUID replier = UUID.randomUUID();
        UUID diary = UUID.randomUUID();

        Notification reply = Notification.reply(author, diary, null, replier, CLOCK).orElseThrow();
        assertThat(reply.type()).isEqualTo(NotificationType.REPLY);
        assertThat(reply.recipientId()).isEqualTo(author);
        assertThat(reply.actorId()).isEqualTo(replier);
        assertThat(Notification.reply(author, diary, null, author, CLOCK)).isEmpty();
    }

    @Test
    void replyPointsToADiaryOrToARecipe() {
        UUID author = UUID.randomUUID();
        UUID recipe = UUID.randomUUID();

        assertThat(Notification.reply(author, null, recipe, UUID.randomUUID(), CLOCK).orElseThrow().recipeId())
                .isEqualTo(recipe);
        assertThatThrownBy(() -> Notification.reply(author, null, null, UUID.randomUUID(), CLOCK))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Notification.reply(author, UUID.randomUUID(), recipe, UUID.randomUUID(), CLOCK))
                .isInstanceOf(DomainException.class);
    }
}
