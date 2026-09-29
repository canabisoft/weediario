import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.setAttribute('data-theme', 'dark');
    document.head.innerHTML = '<meta name="theme-color" content="#0f1411">';
  });

  function create(): ThemeService {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();
    return service;
  }

  it('starts with the theme applied before bootstrap', () => {
    expect(create().theme()).toBe('dark');
  });

  it('applies, persists and paints the browser bar black in night mode', () => {
    const service = create();

    service.select('night');
    TestBed.tick();

    expect(document.documentElement.getAttribute('data-theme')).toBe('night');
    expect(localStorage.getItem('wd-theme')).toBe('night');
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#000000');
  });

  it('night toggle returns to the previous day theme', () => {
    const service = create();
    service.select('light');

    service.toggleNight();
    expect(service.theme()).toBe('night');

    service.toggleNight();
    expect(service.theme()).toBe('light');
  });
});
