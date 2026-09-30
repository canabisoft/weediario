package br.com.weediario.notification.presentation.rest;

import br.com.weediario.notification.application.query.NotificationReadModel.NotificationView;
import br.com.weediario.notification.application.usecase.MyNotificationsUseCase;
import br.com.weediario.notification.infrastructure.push.SseNotificationHub;
import br.com.weediario.shared.application.PageQuery;
import br.com.weediario.shared.application.PageResult;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final MyNotificationsUseCase notifications;
    private final SseNotificationHub hub;

    public NotificationController(MyNotificationsUseCase notifications, SseNotificationHub hub) {
        this.notifications = notifications;
        this.hub = hub;
    }

    @GetMapping
    public PageResult<NotificationView> inbox(@AuthenticationPrincipal Jwt principal,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return notifications.inbox(Viewer.requireId(principal), new PageQuery(page, size));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt principal) {
        return Map.of("unread", notifications.unreadCount(Viewer.requireId(principal)));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt principal) {
        notifications.markAllRead(Viewer.requireId(principal));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        notifications.markRead(Viewer.requireId(principal), id);
    }

    /**
     * Canal em tempo real (Server-Sent Events) com o contador de não lidas. O cliente abre com o Bearer no header
     * (via fetch, não EventSource) e reconecta quando o canal fecha, o que acontece quando o token expira.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt principal, HttpServletResponse response) {
        // Proxies (nginx) não podem segurar os eventos em buffer.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-store");
        Instant expiresAt = principal.getExpiresAt() == null ? Instant.now() : principal.getExpiresAt();
        return hub.open(Viewer.requireId(principal), expiresAt);
    }
}
