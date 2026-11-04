import { NotificationItem } from '../core/api/notifications.api';
import { notificationLink, notificationText, timeAgo } from './notification-text';

const base: NotificationItem = {
  id: 'n1',
  type: 'COMMENT',
  occurrences: 1,
  createdAt: '2026-09-29T12:00:00Z',
  read: false,
};
const sentence = (item: NotificationItem) =>
  notificationText(item)
    .map((part) => part.text)
    .join('');

describe('notificationText', () => {
  it('tells the author which content left the air, without saying who reported it', () => {
    expect(
      sentence({
        ...base,
        type: 'CONTENT_HELD',
        heldType: 'DIARY',
        heldId: 'd1',
        diaryTitle: 'Tenda 80',
      }),
    ).toBe('Seu diário Tenda 80 saiu do ar e aguarda a moderação.');
    expect(
      sentence({
        ...base,
        type: 'CONTENT_HELD',
        heldType: 'COMMENT',
        heldId: 'c1',
        diaryTitle: 'Auto',
      }),
    ).toBe('Seu comentário em Auto saiu do ar e aguarda a moderação.');
    expect(sentence({ ...base, type: 'CONTENT_HELD', heldType: 'PROFILE', heldId: 'u1' })).toBe(
      'Seu perfil saiu do ar e aguarda a moderação.',
    );
  });

  it('names who commented or posted, never who reacted or followed', () => {
    expect(sentence({ ...base, actor: 'maria', diaryTitle: 'Tenda 80' })).toBe(
      'maria comentou em Tenda 80',
    );
    expect(sentence({ ...base, type: 'REACTION', occurrences: 3, diaryTitle: 'Tenda 80' })).toBe(
      'Tenda 80 recebeu 3 reações novas',
    );
    expect(sentence({ ...base, type: 'NEW_FOLLOWER' })).toBe('Você ganhou um seguidor novo');
    expect(
      sentence({
        ...base,
        type: 'FOLLOWED_UPDATE',
        actor: 'joao',
        occurrences: 2,
        diaryTitle: 'Auto',
      }),
    ).toBe('joao postou 2 updates em Auto');
  });

  it('names who replied to my comment, in a diary or in a recipe', () => {
    expect(
      sentence({ ...base, type: 'REPLY', actor: 'ana', diaryId: 'd1', diaryTitle: 'Tenda 80' }),
    ).toBe('ana respondeu seu comentário em Tenda 80');
    expect(
      sentence({
        ...base,
        type: 'REPLY',
        actor: 'ana',
        recipeId: 'r1',
        recipeName: 'Base de flora',
      }),
    ).toBe('ana respondeu seu comentário em Base de flora');
    expect(notificationLink({ ...base, type: 'REPLY', recipeId: 'r1' }, 'eu')).toEqual([
      '/receitas',
      'r1',
    ]);
  });

  it('links to the diary, or to my public profile for new followers', () => {
    expect(notificationLink({ ...base, diaryId: 'd1' }, 'eu')).toEqual(['/diarios', 'd1']);
    expect(notificationLink({ ...base, type: 'NEW_FOLLOWER' }, 'eu')).toEqual(['/growers', 'eu']);
  });
});

describe('timeAgo', () => {
  const now = new Date('2026-09-29T12:00:00Z');

  it('uses short relative times', () => {
    expect(timeAgo('2026-09-29T11:59:30Z', now)).toBe('agora');
    expect(timeAgo('2026-09-29T11:15:00Z', now)).toBe('há 45 min');
    expect(timeAgo('2026-09-29T07:00:00Z', now)).toBe('há 5 h');
    expect(timeAgo('2026-09-28T10:00:00Z', now)).toBe('ontem');
    expect(timeAgo('2026-09-25T12:00:00Z', now)).toBe('há 4 dias');
  });
});
