import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { RankedGrower, RankingSort, levelFor } from './grower-ranking';
import { GROWER_RANKING_SOURCE, GrowerRankingSource } from './grower-ranking.source';
import { GrowersRankingPage } from './growers-ranking.page';

describe('GrowersRankingPage', () => {
  const growers: RankedGrower[] = [
    { username: 'verdejante', hasAvatar: false, diaries: 64, likes: 100, gramsPerPlant: 142 },
    { username: 'kaya', hasAvatar: true, diaries: 27, likes: 500, gramsPerPlant: 210 },
    { username: 'broto_novo', hasAvatar: false, diaries: 1, likes: 900, gramsPerPlant: null },
  ];
  const calls: { sort: RankingSort; query: string }[] = [];

  /** Simula o backend: ordena pelo critério, numera e só depois filtra pelo nome. */
  const source: GrowerRankingSource = {
    load: (sort, query) => {
      calls.push({ sort, query });
      const ranked = [...growers]
        .sort((a, b) => (b[sort] ?? -1) - (a[sort] ?? -1))
        .map((grower, index) => ({ position: index + 1, grower, level: levelFor(grower.diaries) }))
        .filter((entry) => entry.grower.username.includes(query));
      return of({ entries: ranked, total: ranked.length });
    },
  };

  let harness: RouterTestingHarness;

  beforeEach(async () => {
    calls.length = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'growers', component: GrowersRankingPage }], withComponentInputBinding()),
        { provide: GROWER_RANKING_SOURCE, useValue: source },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  async function open(url: string): Promise<HTMLElement> {
    await harness.navigateByUrl(url);
    await new Promise((resolve) => setTimeout(resolve, 250));
    harness.detectChanges();
    return harness.routeNativeElement!;
  }

  function names(root: HTMLElement): string[] {
    return Array.from(root.querySelectorAll('ol > li a[href^="/growers/"]')).map((el) => el.textContent!.trim());
  }

  it('lists growers by diaries by default, with position, level and metrics', async () => {
    const root = await open('/growers');

    expect(names(root)).toEqual(['verdejante', 'kaya', 'broto_novo']);
    const first = root.querySelector('ol > li')!;
    expect(first.textContent).toContain('GrandMaster');
    expect(first.querySelector('[aria-label="Posição 1"]')).not.toBeNull();
    expect(first.textContent).toContain('142');
  });

  it('sends sort and search from the URL to the backend', async () => {
    let root = await open('/growers?ordem=likes');
    expect(names(root)).toEqual(['broto_novo', 'kaya', 'verdejante']);

    root = await open('/growers?ordem=likes&q=kay');
    expect(calls.at(-1)).toEqual({ sort: 'likes', query: 'kay' });
    expect(names(root)).toEqual(['kaya']);
    expect(root.querySelector('[aria-label="Posição 2"]')).not.toBeNull();
  });

  it('shows a dash for growers without a registered harvest', async () => {
    const root = await open('/growers');

    const beginner = root.querySelectorAll('ol > li')[2];
    expect(beginner.querySelector('dd[title="Sem colheita registrada"]')?.textContent?.trim()).toBe('—');
  });

  it('shows an empty state when nobody matches', async () => {
    const root = await open('/growers?q=ninguem');

    expect(root.textContent).toContain('Nenhum grower encontrado');
  });

  it('ignores an unknown sort in the URL', async () => {
    await open('/growers?ordem=hackeado');

    expect(calls.at(-1)?.sort).toBe('diaries');
  });
});
