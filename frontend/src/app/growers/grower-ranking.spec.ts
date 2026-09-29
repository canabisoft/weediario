import { RankedGrower, buildRanking, initialOf, levelFor } from './grower-ranking';

describe('grower ranking', () => {
  const growers: RankedGrower[] = [
    { username: 'ana', diaries: 5, likes: 900, gramsPerPlant: 120 },
    { username: 'bia', diaries: 30, likes: 100, gramsPerPlant: null },
    { username: 'caio', diaries: 5, likes: 400, gramsPerPlant: 200 },
    { username: 'Érico', diaries: 60, likes: 50, gramsPerPlant: 80 },
  ];

  it('orders by the chosen metric, highest first', () => {
    expect(buildRanking(growers, 'likes', '').map((e) => e.grower.username)).toEqual(['ana', 'caio', 'bia', 'Érico']);
    expect(buildRanking(growers, 'gramsPerPlant', '').map((e) => e.grower.username)).toEqual([
      'caio',
      'ana',
      'Érico',
      'bia',
    ]);
  });

  it('gives ties the same position', () => {
    const positions = buildRanking(growers, 'diaries', '').map((e) => [e.grower.username, e.position]);

    expect(positions).toEqual([
      ['Érico', 1],
      ['bia', 2],
      ['ana', 3],
      ['caio', 3],
    ]);
  });

  it('keeps the overall position when filtering by name', () => {
    const result = buildRanking(growers, 'likes', 'CAIO');

    expect(result).toHaveLength(1);
    expect(result[0].position).toBe(2);
  });

  it('ignores accents and case in the search', () => {
    expect(buildRanking(growers, 'diaries', 'erico').map((e) => e.grower.username)).toEqual(['Érico']);
  });

  it('derives the level from published diaries', () => {
    expect(levelFor(0).name).toBe('Iniciante');
    expect(levelFor(3).name).toBe('Cultivador');
    expect(levelFor(25).name).toBe('Guru');
    expect(levelFor(50).name).toBe('GrandMaster');
  });

  it('uses the first letter or digit as avatar fallback', () => {
    expect(initialOf('solo_vivo')).toBe('S');
    expect(initialOf('_420grow')).toBe('4');
    expect(initialOf('érico')).toBe('É');
    expect(initialOf('__')).toBe('?');
  });
});
