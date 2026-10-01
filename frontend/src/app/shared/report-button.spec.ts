import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ModerationApi } from '../core/api/moderation.api';
import { ReportButton } from './report-button';

describe('ReportButton', () => {
  const calls: unknown[][] = [];
  const api: Partial<ModerationApi> = {
    report: (...args: unknown[]) => {
      calls.push(args);
      return of({ hidden: true });
    },
  } as Partial<ModerationApi>;

  beforeEach(() => {
    calls.length = 0;
    TestBed.configureTestingModule({ providers: [{ provide: ModerationApi, useValue: api }] });
  });

  it('requires a reason, sends the report and tells whether it took the content down', async () => {
    const fixture = TestBed.createComponent(ReportButton);
    fixture.componentRef.setInput('targetType', 'COMMENT');
    fixture.componentRef.setInput('targetId', 'c1');
    let hidden: boolean | undefined;
    fixture.componentInstance.reported.subscribe((value) => (hidden = value));
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    root.querySelector('button')!.click();
    fixture.detectChanges();
    const submit = root.querySelector<HTMLButtonElement>('button[type=submit]')!;
    expect(submit.disabled).toBe(true);
    expect(root.textContent).toContain('anônima');

    const commerce = [...root.querySelectorAll('label')].find((l) => l.textContent?.includes('Comércio ilegal'))!;
    commerce.querySelector('input')!.click();
    fixture.detectChanges();
    submit.click();
    fixture.detectChanges();

    expect(calls).toEqual([['COMMENT', 'c1', 'ILLEGAL_COMMERCE']]);
    expect(hidden).toBe(true);
    expect(root.textContent).toContain('Denúncia enviada');
  });
});
