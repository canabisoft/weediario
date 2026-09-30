import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationItem, NotificationsApi } from '../core/api/notifications.api';
import { problemMessage } from '../core/api/problem';
import { AuthService } from '../core/auth/auth.service';
import { NotificationCenter } from '../core/notifications/notification-center';
import { Icon, IconName } from '../shared/icon';
import { notificationLink, notificationText, timeAgo } from './notification-text';

const ICONS: Record<NotificationItem['type'], IconName> = {
  COMMENT: 'chat',
  REACTION: 'flame',
  NEW_FOLLOWER: 'userPlus',
  FOLLOWED_UPDATE: 'diaries',
};

/**
 * Caixa de notificações. Ao abrir, tudo passa a lido (as novas continuam destacadas até sair da página).
 */
@Component({
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NgTemplateOutlet, Icon],
  template: `
    <h1 class="text-2xl font-bold">Notificações</h1>
    <p class="text-fg-muted">
      Comentários, reações e novidades de quem você segue. Reações e seguidores chegam anônimos, do jeito que o resto do app mostra.
    </p>

    @if (items(); as list) {
      <ul class="mt-4 grid gap-2">
        @for (item of list; track item.id) {
          <li>
            @let target = link(item);
            @if (target) {
              <a [routerLink]="target" class="wd-card flex items-start gap-3 hover:border-accent/50" [class.border-accent]="!item.read">
                <ng-container [ngTemplateOutlet]="row" [ngTemplateOutletContext]="{ $implicit: item }" />
              </a>
            } @else {
              <div class="wd-card flex items-start gap-3" [class.border-accent]="!item.read">
                <ng-container [ngTemplateOutlet]="row" [ngTemplateOutletContext]="{ $implicit: item }" />
              </div>
            }
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Nada por aqui ainda. Quando alguém interagir com seus diários, aparece aqui.</li>
        }
      </ul>
      @if (hasMore()) {
        <button type="button" class="wd-button mt-4 w-full bg-surface-raised text-fg" [disabled]="loading()" (click)="loadMore()">
          Carregar mais
        </button>
      }
    } @else if (!error()) {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
    @if (error()) {
      <p role="alert" class="mt-4 text-sm text-danger">{{ error() }}</p>
    }

    <ng-template #row let-item>
      <span class="grid size-10 shrink-0 place-items-center rounded-full bg-accent/15 text-accent">
        <app-icon [name]="icon(item)" class="size-5" />
      </span>
      <span class="min-w-0 flex-1">
        <span class="block">
          @for (part of text(item); track $index) {
            @if (part.strong) {
              <strong class="font-semibold">{{ part.text }}</strong>
            } @else {
              {{ part.text }}
            }
          }
        </span>
        <time class="text-xs text-fg-muted" [attr.datetime]="item.createdAt">{{ when(item.createdAt) }}</time>
      </span>
      @if (!item.read) {
        <span class="mt-2 size-2.5 shrink-0 rounded-full bg-accent" aria-label="Nova"></span>
      }
    </ng-template>
  `,
})
export class NotificationsPage implements OnInit {
  private readonly api = inject(NotificationsApi);
  private readonly center = inject(NotificationCenter);
  private readonly auth = inject(AuthService);

  protected readonly items = signal<NotificationItem[] | null>(null);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private page = 0;

  ngOnInit(): void {
    this.load(0, () => {
      this.center.markAllReadLocally();
      this.api.markAllRead().subscribe({ error: () => undefined });
    });
  }

  protected loadMore(): void {
    this.load(this.page + 1);
  }

  protected text(item: NotificationItem) {
    return notificationText(item);
  }

  protected link(item: NotificationItem): string[] | null {
    return notificationLink(item, this.auth.me()?.username);
  }

  protected icon(item: NotificationItem): IconName {
    return ICONS[item.type];
  }

  protected when(instant: string): string {
    return timeAgo(instant);
  }

  private load(page: number, then?: () => void): void {
    this.loading.set(true);
    this.api.inbox(page).subscribe({
      next: (result) => {
        this.page = page;
        this.items.set([...(page === 0 ? [] : (this.items() ?? [])), ...result.items]);
        this.hasMore.set(page + 1 < result.totalPages);
        this.loading.set(false);
        then?.();
      },
      error: (error) => {
        this.error.set(problemMessage(error, 'Não foi possível carregar as notificações.'));
        this.loading.set(false);
      },
    });
  }
}
