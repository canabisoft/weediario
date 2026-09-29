import { initialOf, levelFor } from './grower-ranking';

describe('grower ranking', () => {
  it('derives the level from published diaries', () => {
    expect(levelFor(0).name).toBe('Iniciante');
    expect(levelFor(3).name).toBe('Cultivador');
    expect(levelFor(10).name).toBe('Mestre');
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
