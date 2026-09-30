package br.com.weediario.notification.infrastructure.push;

import br.com.weediario.notification.application.port.NotificationPush;
import br.com.weediario.notification.application.query.NotificationReadModel;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Conexões Server-Sent Events abertas, por usuário, em memória (uma instância do backend).
 * <p>
 * Privacidade: o canal só carrega o contador de não lidas; o conteúdo é buscado pela API normal.
 * Cada conexão termina quando o access token que a abriu expira (no máximo {@link #MAX_STREAM}): o cliente
 * reconecta com um token novo, então uma sessão encerrada para de receber avisos.
 */
@Component
public class SseNotificationHub implements NotificationPush {

    static final int MAX_STREAMS_PER_USER = 5;
    static final Duration MAX_STREAM = Duration.ofMinutes(30);
    static final Duration MIN_STREAM = Duration.ofSeconds(30);

    private final Map<UUID, Deque<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final NotificationReadModel readModel;
    private final Clock clock;

    public SseNotificationHub(NotificationReadModel readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    /** Abre um canal já com o contador atual. Acima do limite por usuário, o canal mais antigo é fechado. */
    public SseEmitter open(UUID userId, Instant tokenExpiresAt) {
        Duration lifetime = Duration.between(clock.instant(), tokenExpiresAt);
        lifetime = lifetime.compareTo(MAX_STREAM) > 0 ? MAX_STREAM : lifetime.compareTo(MIN_STREAM) < 0 ? MIN_STREAM : lifetime;
        SseEmitter emitter = new SseEmitter(lifetime.toMillis());
        emitter.onCompletion(() -> forget(userId, emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> forget(userId, emitter));

        Deque<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        userStreams.add(emitter);
        while (userStreams.size() > MAX_STREAMS_PER_USER) {
            SseEmitter oldest = userStreams.poll();
            if (oldest != null) {
                oldest.complete();
            }
        }
        send(userId, emitter, SseEmitter.event().name("unread")
                .data(Map.of("unread", readModel.unreadCount(userId)), MediaType.APPLICATION_JSON));
        return emitter;
    }

    @Override
    public void unreadChanged(Collection<UUID> recipientIds) {
        Set<UUID> recipients = new LinkedHashSet<>(recipientIds);
        if (recipients.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(recipients);
                }
            });
        } else {
            deliver(recipients);
        }
    }

    /** Mantém a conexão viva através de proxies que derrubam conexões ociosas. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        streams.forEach((userId, userStreams) ->
                userStreams.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("hb"))));
    }

    int openStreams(UUID userId) {
        Deque<SseEmitter> userStreams = streams.get(userId);
        return userStreams == null ? 0 : userStreams.size();
    }

    private void deliver(Set<UUID> recipients) {
        for (UUID recipient : recipients) {
            Deque<SseEmitter> userStreams = streams.get(recipient);
            if (userStreams == null || userStreams.isEmpty()) {
                continue;
            }
            long unread = readModel.unreadCount(recipient);
            userStreams.forEach(emitter -> send(recipient, emitter, SseEmitter.event().name("unread")
                    .data(Map.of("unread", unread), MediaType.APPLICATION_JSON)));
        }
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException closed) {
            // Cliente foi embora: o próximo envio nem tenta.
            forget(userId, emitter);
        }
    }

    private void forget(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, userStreams) -> {
            userStreams.remove(emitter);
            return userStreams.isEmpty() ? null : userStreams;
        });
    }
}
