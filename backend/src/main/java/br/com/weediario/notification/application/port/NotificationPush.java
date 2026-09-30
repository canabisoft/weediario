package br.com.weediario.notification.application.port;

import java.util.Collection;
import java.util.UUID;

/**
 * Entrega em tempo real. Só avisa que o contador de não lidas mudou (nada de conteúdo no canal);
 * a implementação envia depois do commit, para ninguém ver uma notificação que acabou desfeita.
 */
public interface NotificationPush {

    void unreadChanged(Collection<UUID> recipientIds);
}
