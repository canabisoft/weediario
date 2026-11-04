package br.com.weediario.moderation.application.port;

import br.com.weediario.moderation.application.port.ModeratedContent.ContentInfo;

/** Avisa o autor que o conteúdo dele saiu do ar e aguarda a moderação (e que ele pode se manifestar). */
public interface HoldNotifier {

    void contentHeld(ContentInfo held);
}
