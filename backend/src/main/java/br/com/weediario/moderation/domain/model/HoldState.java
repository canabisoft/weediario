package br.com.weediario.moderation.domain.model;

import br.com.weediario.shared.domain.ModerationAppeals;

import java.time.Instant;

/** Conteúdo retido aguardando a moderação, com a manifestação do autor, se ele já mandou. */
public record HoldState(Instant heldAt, Instant appealedAt) {

    public Instant appealDeadline() {
        return heldAt.plus(ModerationAppeals.WINDOW);
    }

    public boolean appealed() {
        return appealedAt != null;
    }
}
