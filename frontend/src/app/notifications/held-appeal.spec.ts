import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationItem } from '../core/api/notifications.api';
import { HeldAppeal } from './held-appeal';

const held: NotificationItem = {
  id: 'n1',
  type: 'CONTENT_HELD',
  occurrences: 1,
  createdAt: '2026-11-04T12:00:00Z',
  read: false,
  heldType: 'DIARY',
  heldId: 'd1',
  appealUntil: new Date(Date.now() + 3 * 86_400_000).toISOString(),
  appealed: false,
};

describe('HeldAppeal', () => {
  function render(item: NotificationItem) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(HeldAppeal);
    fixture.componentRef.setInput('item', item);
    fixture.detectChanges();
    return {
      fixture,
      root: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
    };
  }

  it('sends the author version to the moderation once and confirms it', () => {
    const { fixture, root, http } = render(held);
    const textarea = root.querySelector('textarea')!;
    textarea.value = 'É só o meu diário de cultivo, não vendo nada.';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    root.querySelector('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/me/moderation/appeals');
    expect(request.request.body).toEqual({
      targetType: 'DIARY',
      targetId: 'd1',
      text: 'É só o meu diário de cultivo, não vendo nada.',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(root.textContent).toContain('Sua versão foi enviada');
    expect(root.querySelector('form')).toBeNull();
    http.verify();
  });

  it('has nothing to send once the moderation decided or the author already answered', () => {
    expect(render({ ...held, appealUntil: undefined }).root.textContent).toContain(
      'A moderação já decidiu',
    );
    TestBed.resetTestingModule();
    expect(render({ ...held, appealed: true }).root.textContent).toContain(
      'Sua versão foi enviada',
    );
  });
});
