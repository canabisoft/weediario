import { TestBed } from '@angular/core/testing';
import { TestingProgressBar } from './testing-progress';

describe('TestingProgressBar', () => {
  it('shows how many outside growers and reviews are still missing', () => {
    const fixture = TestBed.createComponent(TestingProgressBar);
    fixture.componentRef.setInput('progress', { testers: 2, testersRequired: 3, reviews: 0, reviewsRequired: 1 });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Cultivadores de fora do breeder');
    expect(root.textContent).toContain('2/3');
    expect(root.textContent).toContain('0/1');
    expect(root.querySelectorAll('.bg-accent')).toHaveLength(2);
    // Sem estilo inline (CSP): só classes.
    expect(root.querySelector('[style]')).toBeNull();
  });

  it('never shows more than the requirement', () => {
    const fixture = TestBed.createComponent(TestingProgressBar);
    fixture.componentRef.setInput('progress', { testers: 5, testersRequired: 3, reviews: 1, reviewsRequired: 1 });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('3/3');
  });
});
