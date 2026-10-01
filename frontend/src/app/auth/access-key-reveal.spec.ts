import { TestBed } from '@angular/core/testing';
import { AccessKeyReveal } from './access-key-reveal';

describe('AccessKeyReveal', () => {
  const KEY = 'wd_sec_' + 'A'.repeat(43);

  it('hides the key by default and only continues after the user confirms saving it', async () => {
    const fixture = TestBed.createComponent(AccessKeyReveal);
    fixture.componentRef.setInput('username', 'maria');
    fixture.componentRef.setInput('accessKey', KEY);
    let done = 0;
    fixture.componentInstance.done.subscribe(() => done++);
    fixture.detectChanges();
    // Os campos de um <form> com ngModel se registram de forma assíncrona.
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    const field = root.querySelector<HTMLInputElement>('#access-key')!;
    expect(field.value).toBe(KEY);
    expect(field.getAttribute('type')).toBe('password');
    expect(field.getAttribute('autocomplete')).toBe('new-password');
    expect(root.textContent).toContain('aparece só agora');

    const submit = root.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(submit.disabled).toBe(true);
    root.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(submit.disabled).toBe(false);

    submit.click();
    expect(done).toBe(1);
  });
});
