import { BIP39_PORTUGUESE } from './bip39-portuguese';
import { bip39Seed, checkPhrase, deriveVerifier, entropyToWords, generatePhrase } from './recovery-phrase';

/** Entropia zerada: no BIP-39 vira 11× a primeira palavra + a palavra 3 (checksum 0011), como "abandon … about". */
const ZERO_PHRASE = [...Array(11).fill('abacate'), 'abater'];

describe('recovery phrase (BIP-39)', () => {
  it('uses the official 2048-word Portuguese list', () => {
    expect(BIP39_PORTUGUESE.length).toBe(2048);
    expect(new Set(BIP39_PORTUGUESE).size).toBe(2048);
    expect(BIP39_PORTUGUESE[0]).toBe('abacate');
    expect(BIP39_PORTUGUESE[2047]).toBe('zumbido');
  });

  it('encodes entropy with the BIP-39 checksum', async () => {
    expect(await entropyToWords(new Uint8Array(16), BIP39_PORTUGUESE)).toEqual(ZERO_PHRASE);
  });

  it('derives the standard BIP-39 seed (official test vector)', async () => {
    const vector =
      'c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04';
    expect(await bip39Seed('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about', 'TREZOR')).toBe(vector);
  });

  it('generates valid 12-word phrases whose verifier is 128 hex chars and never contains the words', async () => {
    const words = await generatePhrase();
    expect(words.length).toBe(12);
    const check = await checkPhrase(words.join(' '));
    expect(check).toEqual({ ok: true, phrase: words.join(' ') });
    const verifier = await deriveVerifier(words.join(' '));
    expect(verifier).toMatch(/^[0-9a-f]{128}$/);
    expect(words.some((word) => verifier.includes(word))).toBe(false);
  });

  it('normalizes what was typed and accepts the first four letters of each word', async () => {
    const typed = ' ABACATE, abac abacate abacate abacate abacate abacate abacate abacate abacate abacate abatê ';
    expect(await checkPhrase(typed)).toEqual({ ok: true, phrase: ZERO_PHRASE.join(' ') });
  });

  it('catches wrong word count, unknown words and a broken checksum', async () => {
    expect(await checkPhrase('abacate abaixo')).toEqual(failure(false, /12 palavras/));
    expect(await checkPhrase([...ZERO_PHRASE.slice(0, 11), 'xyzw'].join(' '))).toEqual(failure(false, /12ª palavra/));
    expect(await checkPhrase([...ZERO_PHRASE.slice(0, 11), 'abaixo'].join(' '))).toEqual(failure(false, /não confere/));
  });
});

function failure(ok: boolean, message: RegExp) {
  return expect.objectContaining({ ok, message: expect.stringMatching(message) });
}
