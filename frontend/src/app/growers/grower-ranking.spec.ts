import { initialOf, levelFor, nextLevel } from './grower-ranking';

describe('grower ranking', () => {
  it('derives the level from published diaries', () => {
    expect(levelFor(0).name).toBe('Iniciante');
    expect(levelFor(3).name).toBe('Cultivador');
    expect(levelFor(10).name).toBe('Mestre');
    expect(levelFor(25).name).toBe('Guru');
    expect(levelFor(50).name).toBe('GrandMaster');
  });

  it('tells how many public diaries are missing for the next level', () => {
    expect(nextLevel(0)).toEqual({ next: expect.objectContaining({ name: 'Cultivador' }), missing: 3, progress: 0 });
    expect(nextLevel(17)).toEqual({ next: expect.objectContaining({ name: 'Guru' }), missing: 8, progress: 7 / 15 });
    expect(nextLevel(50)).toBeNull();
  });

  it('uses the first letter or digit as avatar fallback', () => {
    expect(initialOf('solo_vivo')).toBe('S');
    expect(initialOf('_420grow')).toBe('4');
    expect(initialOf('érico')).toBe('É');
    expect(initialOf('__')).toBe('?');
  });
});
