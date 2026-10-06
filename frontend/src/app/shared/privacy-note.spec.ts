import { TestBed } from '@angular/core/testing';
import { PRIVACY_MESSAGES, PrivacyNote } from './privacy-note';

describe('PrivacyNote', () => {
  afterEach(() => vi.useRealTimers());

  it('alternates between minimal sign-up, clean photos and no commerce, never repeating a theme in a row', () => {
    const theme = (message: string) =>
      /apelido|e-mail|pseudônimo/i.test(message) ? 'account' : /foto|metadados/i.test(message) ? 'photos' : /vend|negoci|negóci|feira|denunc|pix/i.test(message) ? 'rules' : null;

    PRIVACY_MESSAGES.forEach((message, i) => {
      const next = PRIVACY_MESSAGES[(i + 1) % PRIVACY_MESSAGES.length];
      expect(theme(message)).not.toBeNull();
      expect(theme(message)).not.toBe(theme(next));
    });
    expect(new Set(PRIVACY_MESSAGES.map(theme)).size).toBe(3);
  });

  it('never promises "no logs" (the Marco Civil access log exists)', () => {
    expect(PRIVACY_MESSAGES.some((message) => /logs?|sem rastro/i.test(message))).toBe(false);
  });

  it('never softens the promise with "dentro do possível"', () => {
    expect(PRIVACY_MESSAGES.some((message) => /dentro do poss/i.test(message))).toBe(false);
  });

  it('rotates to the next message over time', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(PrivacyNote);
    fixture.detectChanges();
    const text = () => fixture.nativeElement.querySelectorAll('p')[1].textContent.trim();
    const first = text();

    vi.advanceTimersByTime(7300);
    fixture.detectChanges();

    expect(PRIVACY_MESSAGES).toContain(text());
    expect(text()).not.toBe(first);
  });
});
