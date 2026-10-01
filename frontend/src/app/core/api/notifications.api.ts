import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';

export type NotificationType = 'COMMENT' | 'REACTION' | 'NEW_FOLLOWER' | 'FOLLOWED_UPDATE' | 'REPLY';

/**
 * Notificação da caixa de entrada. Reações e novos seguidores são anônimos: nunca trazem {@code actor}.
 * {@code occurrences} soma as vezes que aconteceu enquanto a notificação não foi lida. Respostas a comentários de
 * receita trazem {@code recipeId}/{@code recipeName} no lugar do diário.
 */
export interface NotificationItem {
  readonly id: string;
  readonly type: NotificationType;
  readonly diaryId?: string;
  readonly diaryTitle?: string;
  readonly recipeId?: string;
  readonly recipeName?: string;
  readonly actor?: string;
  readonly occurrences: number;
  readonly createdAt: string;
  readonly read: boolean;
}

export const NOTIFICATIONS_STREAM_URL = '/api/v1/me/notifications/stream';

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  inbox(page = 0): Observable<Page<NotificationItem>> {
    return this.http.get<Page<NotificationItem>>('/api/v1/me/notifications', { params: { page, size: 20 } });
  }

  unreadCount(): Observable<{ unread: number }> {
    return this.http.get<{ unread: number }>('/api/v1/me/notifications/unread-count');
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>('/api/v1/me/notifications/read', {});
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/me/notifications/${id}/read`, {});
  }
}
