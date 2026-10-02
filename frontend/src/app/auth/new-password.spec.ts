import { TestBed } from '@angular/core/testing';
import { NewPassword } from './new-password';

describe('NewPassword', () => {
  async function render() {
    const fixture = TestBed.createComponent(NewPassword);
    const emitted: (string | null)[] = [];
    fixture.componentInstance.changed.subscribe((value) => emitted.push(value));
    fixture.detectChanges();
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const [password, confirmation] = [...root.querySelectorAll<HTMLInputElement>('input')];
    return { fixture, root, password, confirmation, last: () => emitted.at(-1) };
  }

  async function type(fixture: { detectChanges(): void; whenStable(): Promise<unknown> }, input: HTMLInputElement, value: string) {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('only emits the password once it is long enough and both fields match', async () => {
    const { fixture, root, password, confirmation, last } = await render();
    expect(last()).toBeNull();

    await type(fixture, password, 'curta');
    await type(fixture, confirmation, 'curta');
    expect(last()).toBeNull();
    expect(root.textContent).toContain('Pelo menos 10 caracteres');

    await type(fixture, password, 'tres vasos na varanda');
    await type(fixture, confirmation, 'tres vasos na varandA');
    expect(last()).toBeNull();
    expect(root.textContent).toContain('não batem');

    await type(fixture, confirmation, 'tres vasos na varanda');
    expect(last()).toBe('tres vasos na varanda');
  });
});
