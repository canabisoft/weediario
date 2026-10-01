import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { DiaryComments } from './diary-comments';

const comment = (id: string, extra: object = {}) => ({
  id,
  author: 'ana',
  authorHasAvatar: false,
  byDiaryAuthor: false,
  body: 'Comentário ' + id,
  createdAt: '2026-09-29T10:00:00Z',
  deletable: false,
  ...extra,
});

function setup(loggedIn = true) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: AuthService, useValue: { isLoggedIn: () => loggedIn, isModerator: () => false } },
    ],
  });
  const fixture = TestBed.createComponent(DiaryComments);
  fixture.componentRef.setInput('diaryId', 'd1');
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController), element: fixture.nativeElement as HTMLElement };
}

describe('DiaryComments', () => {
  it('shows replies indented under the root comment and loads the rest on demand', () => {
    const { fixture, http, element } = setup();
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments').flush({
      items: [comment('c1', { replyCount: 2, replies: [comment('c2', { parentId: 'c1', author: 'bia', byDiaryAuthor: true })] })],
      page: 0, size: 30, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();

    const replies = element.querySelector('[aria-label="Respostas ao comentário de ana"]')!;
    expect(replies.textContent).toContain('Comentário c2');
    expect(replies.textContent).toContain('autor');
    const more = Array.from(element.querySelectorAll('button')).find((b) => b.textContent?.includes('Ver mais 1 resposta'))!;
    more.click();
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments/c1/replies').flush({
      items: [comment('c2', { parentId: 'c1' }), comment('c3', { parentId: 'c1' })],
      page: 0, size: 50, totalItems: 2, totalPages: 1,
    });
    fixture.detectChanges();
    expect(element.querySelector('[aria-label="Respostas ao comentário de ana"]')!.textContent).toContain('Comentário c3');
    expect(element.textContent).not.toContain('Ver mais');
  });

  it('replies inline with the answered comment id and reloads the conversation', () => {
    const { fixture, http, element } = setup();
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments').flush({
      items: [comment('c1')], page: 0, size: 30, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();

    (element.querySelector('[aria-label="Responder ana"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const textarea = element.querySelector('textarea#reply-c1') as HTMLTextAreaElement;
    expect(element.querySelector('label[for="reply-c1"]')?.textContent).toContain('Respondendo a ana');
    textarea.value = 'Valeu pela dica';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    (textarea.closest('form') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const post = http.expectOne({ method: 'POST', url: '/api/v1/diaries/d1/comments' });
    expect(post.request.body).toEqual({ body: 'Valeu pela dica', parentId: 'c1' });
    post.flush({ id: 'c2' });
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments').flush({
      items: [comment('c1', { replyCount: 1, replies: [comment('c2', { parentId: 'c1', body: 'Valeu pela dica' })] })],
      page: 0, size: 30, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    expect(element.querySelector('textarea#reply-c1')).toBeNull();
    expect(element.textContent).toContain('Valeu pela dica');
  });

  it('toggles anonymous reactions on a reply', () => {
    const { fixture, http, element } = setup();
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments').flush({
      items: [
        comment('c1', {
          replyCount: 1,
          replies: [comment('c2', { parentId: 'c1', author: 'bia', reactions: { counts: { FIRE: 0, HERB: 1, CLAP: 0 }, mine: ['HERB'] } })],
        }),
      ],
      page: 0, size: 30, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();

    const group = element.querySelector('[aria-label="Reações ao comentário de bia"]')!;
    const herb = group.querySelector('[aria-label="Erva: 1"]') as HTMLButtonElement;
    expect(herb.getAttribute('aria-pressed')).toBe('true');
    herb.click();
    http.expectOne({ method: 'DELETE', url: '/api/v1/diaries/d1/comments/c2/reactions/HERB' })
      .flush({ counts: { FIRE: 0, HERB: 0, CLAP: 0 }, mine: [] });
    fixture.detectChanges();
    expect(group.querySelector('[aria-label="Erva: 0"]')?.getAttribute('aria-pressed')).toBe('false');
  });

  it('hides reply and reaction buttons for visitors', () => {
    const { fixture, http, element } = setup(false);
    http.expectOne((req) => req.url === '/api/v1/diaries/d1/comments').flush({
      items: [comment('c1', { reactions: { counts: { FIRE: 3, HERB: 0, CLAP: 0 }, mine: [] } })],
      page: 0, size: 30, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();

    expect(element.querySelector('[aria-label="Responder ana"]')).toBeNull();
    expect(element.querySelector('[aria-label="Fogo: 3"]')?.tagName).toBe('SPAN');
    expect(element.querySelectorAll('app-comment-reactions button').length).toBe(0);
  });
});
