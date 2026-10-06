import { RegistrationChallenge, solveChallenge } from './registration-challenge';

async function sha256Hex(text: string): Promise<string> {
  const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(hash), (b) => b.toString(16).padStart(2, '0')).join('');
}

async function challengeFor(number: number, maxNumber: number): Promise<RegistrationChallenge> {
  const salt = '0123456789abcdef01234567?expires=1999999999';
  return {
    algorithm: 'SHA-256',
    challenge: await sha256Hex(salt + number),
    salt,
    maxNumber,
    signature: 'f'.repeat(64),
  };
}

describe('registration challenge', () => {
  it('finds the number across batches and keeps the signed fields', async () => {
    const challenge = await challengeFor(1234, 2000);
    const solution = await solveChallenge(challenge);
    expect(solution).toEqual({
      challenge: challenge.challenge,
      salt: challenge.salt,
      number: 1234,
      signature: challenge.signature,
    });
  });

  it('finds the edges (0 and maxNumber)', async () => {
    expect((await solveChallenge(await challengeFor(0, 10))).number).toBe(0);
    expect((await solveChallenge(await challengeFor(10, 10))).number).toBe(10);
  });

  it('gives up when the number is out of range', async () => {
    await expect(solveChallenge(await challengeFor(11, 10))).rejects.toThrow();
  });
});
