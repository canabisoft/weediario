package br.com.weediario.shared.domain;

import java.time.Duration;

/**
 * Contraditório na moderação (Termos de Uso, cláusula 9): o autor de um conteúdo retido é avisado e pode mandar a
 * sua versão à moderação dentro deste prazo, contado de quando o conteúdo saiu do ar. Compartilhado pela moderação,
 * que aceita ou recusa a manifestação, e pelas notificações, que mostram o prazo.
 */
public final class ModerationAppeals {

    public static final Duration WINDOW = Duration.ofDays(5);
    public static final int TEXT_MAX_LENGTH = 2000;

    private ModerationAppeals() {
    }
}
