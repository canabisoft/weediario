import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NOTIFICATIONS_STREAM_URL, NotificationsApi } from '../api/notifications.api';
import { AuthService } from '../auth/auth.service';
import { ConnectivityService } from '../network/connectivity.service';
import { parseSse } from './sse';

const FIRST_RETRY_MS = 2_000;
const MAX_RETRY_MS = 60_000;
/** Fim normal do canal (o token que o abriu expirou): reconecta logo, já com token novo. */
const RECONNECT_AFTER_END_MS = 500;

/**
 * Contador de notificações não lidas, atualizado em tempo real por Server-Sent Events.
 * <p>
 * Usa {@code fetch} em vez de {@code EventSource} porque o access token vive só em memória e precisa ir no
 * header {@code Authorization} (EventSource não manda headers, e token na URL acabaria em logs de proxy).
 * O canal só traz o número; a lista é buscada pela API quando a pessoa abre as notificações.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenter {
  private readonly auth = inject(AuthService);
  private readonly api = inject(NotificationsApi);
  private readonly connectivity = inject(ConnectivityService);

  readonly unread = signal(0);
  /** Canal em tempo real aberto. */
  readonly live = signal(false);

  private controller: AbortController | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private retryDelay = FIRST_RETRY_MS;

  constructor() {
    effect(() => {
      const active = this.auth.isLoggedIn() && this.connectivity.online();
      const loggedOut = !this.auth.isLoggedIn();
      untracked(() => {
        if (active) {
          this.start();
        } else {
          this.stop();
          if (loggedOut) {
            this.unread.set(0);
          }
        }
      });
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  /** Ao abrir a caixa e marcar tudo como lido, o contador zera na hora (o canal confirma depois). */
  markAllReadLocally(): void {
    this.unread.set(0);
  }

  private start(): void {
    if (this.controller) {
      return;
    }
    this.controller = new AbortController();
    // Contador inicial pela API: o sino funciona mesmo se o canal em tempo real não abrir.
    this.api.unreadCount().subscribe({ next: ({ unread }) => this.unread.set(unread), error: () => undefined });
    void this.connect(this.controller);
  }

  private stop(): void {
    this.controller?.abort();
    this.controller = null;
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.live.set(false);
  }

  private async connect(controller: AbortController): Promise<void> {
    try {
      let response = await this.open(this.auth.accessToken(), controller.signal);
      if (response.status === 401) {
        const renewed = await firstValueFrom(this.auth.refresh());
        if (!renewed || controller.signal.aborted) {
          return;
        }
        response = await this.open(renewed, controller.signal);
      }
      if (!response.ok || !response.body) {
        throw new Error(`stream ${response.status}`);
      }
      this.live.set(true);
      this.retryDelay = FIRST_RETRY_MS;
      await this.read(response.body);
      this.scheduleReconnect(controller, RECONNECT_AFTER_END_MS);
    } catch {
      if (!controller.signal.aborted) {
        this.scheduleReconnect(controller, this.retryDelay);
        this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MS);
      }
    } finally {
      if (this.controller === controller) {
        this.live.set(false);
      }
    }
  }

  private open(token: string | null, signal: AbortSignal): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'text/event-stream', 'ngsw-bypass': 'true' };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return fetch(NOTIFICATIONS_STREAM_URL, { headers, signal, cache: 'no-store', credentials: 'same-origin' });
  }

  private async read(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) {
        return;
      }
      const parsed = parseSse(buffer + decoder.decode(value, { stream: true }));
      buffer = parsed.rest;
      for (const { event, data } of parsed.events) {
        if (event === 'unread') {
          const unread = Number(JSON.parse(data).unread);
          if (Number.isFinite(unread)) {
            this.unread.set(unread);
          }
        }
      }
    }
  }

  private scheduleReconnect(controller: AbortController, delay: number): void {
    if (controller.signal.aborted || this.controller !== controller) {
      return;
    }
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (this.controller === controller && !controller.signal.aborted) {
        void this.connect(controller);
      }
    }, delay);
  }
}
