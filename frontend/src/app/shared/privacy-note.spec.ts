import { TestBed } from '@angular/core/testing';
import { PRIVACY_MESSAGES, PrivacyNote } from './privacy-note';

describe('PrivacyNote', () => {
  afterEach(() => vi.useRealTimers());

  it('alternates between the no-logs and the photo-metadata messages', () => {
    const aboutLogs = (message: string) => /log/i.test(message);
    const aboutPhotos = (message: string) => /foto|metadados/i.test(message);

    PRIVACY_MESSAGES.forEach((message, i) => {
      const next = PRIVACY_MESSAGES[(i + 1) % PRIVACY_MESSAGES.length];
      expect(aboutLogs(message) !== aboutLogs(next)).toBe(true);
      expect(aboutLogs(message) || aboutPhotos(message)).toBe(true);
    });
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
