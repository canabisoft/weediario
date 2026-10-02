import { TestBed } from '@angular/core/testing';
import { RecoveryPhraseCard } from './recovery-phrase-card';

describe('RecoveryPhraseCard', () => {
  async function render() {
    const fixture = TestBed.createComponent(RecoveryPhraseCard);
    let verifier: string | undefined;
    fixture.componentInstance.verified.subscribe((value) => (verifier = value));
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelectorAll('ol li').length).toBe(12);
    });
    return { fixture, root: fixture.nativeElement as HTMLElement, verifier: () => verifier };
  }

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('only emits the derived verifier after the checked words match and the user confirms saving them', async () => {
    const { fixture, root, verifier } = await render();
    const words = [...root.querySelectorAll('ol li span.font-semibold')].map((el) => el.textContent!.trim());
    const button = () => [...root.querySelectorAll('button')].find((b) => b.textContent!.includes('Continuar'))!;
    expect(root.textContent).toContain('único jeito de recuperar');

    const inputs = [...root.querySelectorAll<HTMLInputElement>('input[id^="word-"]')];
    expect(inputs.length).toBe(3);
    inputs.forEach((input) => type(input, 'errada'));
    root.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(button().disabled).toBe(true);

    inputs.forEach((input) => type(input, ` ${words[Number(input.id.slice(5))].toUpperCase()} `));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(button().disabled).toBe(false);

    button().click();
    await vi.waitFor(() => expect(verifier()).toMatch(/^[0-9a-f]{128}$/));
    expect(words.some((word) => verifier()!.includes(word))).toBe(false);
  });
});
