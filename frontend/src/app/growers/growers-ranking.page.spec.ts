import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { RankedGrower } from './grower-ranking';
import { GROWER_RANKING_SOURCE } from './grower-ranking.source';
import { GrowersRankingPage } from './growers-ranking.page';

describe('GrowersRankingPage', () => {
  const growers: RankedGrower[] = [
    { username: 'verdejante', diaries: 64, likes: 100, gramsPerPlant: 142 },
    { username: 'broto_novo', diaries: 1, likes: 900, gramsPerPlant: null },
    { username: 'kaya', diaries: 27, likes: 500, gramsPerPlant: 210, avatarUrl: 'https://invalid.example/a.jpg' },
  ];

  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'growers', component: GrowersRankingPage }], withComponentInputBinding()),
        { provide: GROWER_RANKING_SOURCE, useValue: { load: () => of(growers) } },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  function names(): string[] {
    return Array.from(harness.routeNativeElement!.querySelectorAll('ol > li p.font-semibold')).map(
      (el) => el.textContent!.trim(),
    );
  }

  it('lists growers by diaries by default, with position, level and metrics', async () => {
    await harness.navigateByUrl('/growers');

    expect(names()).toEqual(['verdejante', 'kaya', 'broto_novo']);
    const first = harness.routeNativeElement!.querySelector('ol > li')!;
    expect(first.textContent).toContain('GrandMaster');
    expect(first.querySelector('[aria-label="Posição 1"]')).not.toBeNull();
    expect(first.textContent).toContain('142');
  });

  it('reads sort and search from the URL', async () => {
    await harness.navigateByUrl('/growers?ordem=likes');
    expect(names()).toEqual(['broto_novo', 'kaya', 'verdejante']);

    await harness.navigateByUrl('/growers?ordem=likes&q=kay');
    expect(names()).toEqual(['kaya']);
    expect(harness.routeNativeElement!.querySelector('[aria-label="Posição 2"]')).not.toBeNull();
  });

  it('shows a dash for growers without a registered harvest', async () => {
    await harness.navigateByUrl('/growers');

    const beginner = harness.routeNativeElement!.querySelectorAll('ol > li')[2];
    expect(beginner.querySelector('dd[title="Sem colheita registrada"]')?.textContent?.trim()).toBe('—');
  });

  it('falls back to the initial and renders the avatar image without referrer', async () => {
    await harness.navigateByUrl('/growers');

    const kaya = harness.routeNativeElement!.querySelectorAll('ol > li')[1];
    expect(kaya.textContent).toContain('K');
    expect(kaya.querySelector('img')?.getAttribute('referrerpolicy')).toBe('no-referrer');
  });

  it('shows an empty state when nobody matches', async () => {
    await harness.navigateByUrl('/growers?q=ninguem');

    expect(harness.routeNativeElement!.textContent).toContain('Nenhum grower encontrado');
  });

  it('ignores an unknown sort in the URL', async () => {
    await harness.navigateByUrl('/growers?ordem=hackeado');

    expect(names()[0]).toBe('verdejante');
  });
});
