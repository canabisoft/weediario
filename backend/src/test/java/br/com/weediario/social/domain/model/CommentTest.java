package br.com.weediario.social.domain.model;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VISITOR = UUID.randomUUID();
    private static final CommentableDiary DIARY = new CommentableDiary(UUID.randomUUID(), OWNER, true);

    @Test
    void replyPointsToTheRootComment() {
        Comment root = Comment.post(DIARY, VISITOR, "Qual EC na flora?", CLOCK);
        Comment reply = Comment.reply(DIARY, root, OWNER, "1.8", CLOCK);

        assertThat(root.isReply()).isFalse();
        assertThat(root.rootId()).isEqualTo(root.id());
        assertThat(reply.isReply()).isTrue();
        assertThat(reply.parentId()).contains(root.id());
        assertThat(reply.rootId()).isEqualTo(root.id());
    }

    @Test
    void answeringAReplyStaysOnTheSameRootNeverGoingDeeperThanOneLevel() {
        Comment root = Comment.post(DIARY, VISITOR, "Qual EC na flora?", CLOCK);
        Comment reply = Comment.reply(DIARY, root, OWNER, "1.8", CLOCK);
        Comment replyToReply = Comment.reply(DIARY, reply, VISITOR, "Valeu!", CLOCK);

        assertThat(replyToReply.parentId()).contains(root.id());
    }

    @Test
    void cannotAnswerACommentOfAnotherDiaryOrOfAPrivateDiary() {
        Comment elsewhere = Comment.post(new CommentableDiary(UUID.randomUUID(), OWNER, true), VISITOR, "oi", CLOCK);
        assertThatThrownBy(() -> Comment.reply(DIARY, elsewhere, VISITOR, "oi", CLOCK))
                .isInstanceOf(DomainException.class);

        CommentableDiary privateDiary = new CommentableDiary(UUID.randomUUID(), OWNER, false);
        Comment ownerNote = Comment.post(privateDiary, OWNER, "nota", CLOCK);
        assertThatThrownBy(() -> Comment.reply(privateDiary, ownerNote, VISITOR, "oi", CLOCK))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void heldCommentIsVisibleOnlyToTheAuthor() {
        Comment comment = Comment.post(DIARY, VISITOR, "anúncio?", CLOCK);
        comment.holdForModeration();

        assertThat(comment.isVisibleTo(VISITOR)).isTrue();
        assertThat(comment.isVisibleTo(OWNER)).isFalse();
    }

    @Test
    void commentCannotAnswerItself() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> Comment.restore(id, DIARY.diaryId(), id, VISITOR, "eco", false, CLOCK.instant()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void authorDiaryOwnerAndModeratorsCanDeleteReplies() {
        Comment root = Comment.post(DIARY, OWNER, "Semana 3", CLOCK);
        Comment reply = Comment.reply(DIARY, root, VISITOR, "Bonita!", CLOCK);
        UUID stranger = UUID.randomUUID();

        assertThat(reply.canBeDeletedBy(VISITOR, DIARY, false)).isTrue();
        assertThat(reply.canBeDeletedBy(OWNER, DIARY, false)).isTrue();
        assertThat(reply.canBeDeletedBy(stranger, DIARY, true)).isTrue();
        assertThat(reply.canBeDeletedBy(stranger, DIARY, false)).isFalse();
    }
}
