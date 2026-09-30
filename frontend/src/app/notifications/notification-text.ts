import { NotificationItem } from '../core/api/notifications.api';

/** Pedaço da frase: texto comum ou o nome de alguém/algo em destaque. */
export interface TextPart {
  readonly text: string;
  readonly strong?: boolean;
}

/**
 * Frase da notificação. Reações e seguidores são anônimos por design: a frase nunca diz quem foi.
 */
export function notificationText(item: NotificationItem): TextPart[] {
  const title: TextPart = { text: item.diaryTitle ?? 'seu diário', strong: !!item.diaryTitle };
  const n = item.occurrences;
  switch (item.type) {
    case 'COMMENT':
      return [{ text: item.actor ?? 'Alguém', strong: true }, { text: ' comentou em ' }, title];
    case 'REACTION':
      return [title, { text: n === 1 ? ' recebeu uma reação nova' : ` recebeu ${n} reações novas` }];
    case 'NEW_FOLLOWER':
      return [{ text: n === 1 ? 'Você ganhou um seguidor novo' : `Você ganhou ${n} seguidores novos` }];
    case 'FOLLOWED_UPDATE':
      return [
        { text: item.actor ?? 'Um grower que você segue', strong: true },
        { text: n === 1 ? ' postou um update em ' : ` postou ${n} updates em ` },
        title,
      ];
  }
}

/** Para onde a notificação leva. */
export function notificationLink(item: NotificationItem, myUsername: string | undefined): string[] | null {
  if (item.diaryId) {
    return ['/diarios', item.diaryId];
  }
  return item.type === 'NEW_FOLLOWER' && myUsername ? ['/growers', myUsername] : null;
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' });

/** "agora", "há 5 min", "há 3 h", "ontem", "há 4 dias" e, depois de uma semana, a data. */
export function timeAgo(instant: string, now: Date = new Date()): string {
  const elapsed = now.getTime() - new Date(instant).getTime();
  if (elapsed < MINUTE) {
    return 'agora';
  }
  if (elapsed < HOUR) {
    return `há ${Math.floor(elapsed / MINUTE)} min`;
  }
  if (elapsed < DAY) {
    return `há ${Math.floor(elapsed / HOUR)} h`;
  }
  const days = Math.floor(elapsed / DAY);
  if (days === 1) {
    return 'ontem';
  }
  return days < 7 ? `há ${days} dias` : shortDate.format(new Date(instant));
}
