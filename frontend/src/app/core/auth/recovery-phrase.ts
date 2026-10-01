/**
 * Frase de recuperação de 12 palavras (BIP-39, lista em português).
 * <p>
 * Tudo acontece no navegador: a frase é gerada aqui (128 bits do {@code crypto.getRandomValues}, mais o checksum do
 * BIP-39) e o servidor só recebe o <em>derivado</em> dela — a seed BIP-39 (PBKDF2-HMAC-SHA512, 2048 iterações) com a
 * passphrase {@link PASSPHRASE}. As palavras nunca saem do aparelho, nem no cadastro nem na recuperação.
 */

export const PHRASE_WORDS = 12;
/** Mesma do backend (RecoveryVerifier.PASSPHRASE): separa o derivado do weediário de uma carteira com a mesma frase. */
export const PASSPHRASE = 'weediario-recovery';
const ENTROPY_BYTES = 16;
const ITERATIONS = 2048;

export type PhraseCheck = { readonly ok: true; readonly phrase: string } | { readonly ok: false; readonly message: string };

async function wordlist(): Promise<readonly string[]> {
  return (await import('./bip39-portuguese')).BIP39_PORTUGUESE;
}

/** Gera uma frase nova. */
export async function generatePhrase(): Promise<string[]> {
  return entropyToWords(crypto.getRandomValues(new Uint8Array(ENTROPY_BYTES)), await wordlist());
}

/** Codificação do BIP-39: entropia + primeiros bits do SHA-256 dela, em blocos de 11 bits. */
export async function entropyToWords(entropy: Uint8Array<ArrayBuffer>, list: readonly string[]): Promise<string[]> {
  const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', entropy));
  const checksumBits = (entropy.length * 8) / 32;
  const bits = toBits(entropy) + toBits(hash.subarray(0, 1)).slice(0, checksumBits);
  return bits.match(/.{11}/g)!.map((chunk) => list[parseInt(chunk, 2)]);
}

/**
 * Normaliza o que a pessoa digitou (maiúsculas, acentos, vírgulas, espaços extras) e confere as palavras e o
 * checksum, que pega troca ou erro de digitação. Como no BIP-39, as 4 primeiras letras já identificam a palavra.
 */
export async function checkPhrase(input: string): Promise<PhraseCheck> {
  const typed = input
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .split(/[^a-z]+/)
    .filter(Boolean);
  if (typed.length !== PHRASE_WORDS) {
    return { ok: false, message: `A frase tem ${PHRASE_WORDS} palavras (você informou ${typed.length}).` };
  }
  const list = await wordlist();
  const words: string[] = [];
  for (const [position, word] of typed.entries()) {
    const found = list.includes(word) ? word : word.length >= 4 ? list.find((candidate) => candidate.startsWith(word.slice(0, 4))) : undefined;
    if (!found) {
      return { ok: false, message: `A ${position + 1}ª palavra ("${word}") não faz parte da lista. Confira o que você anotou.` };
    }
    words.push(found);
  }
  const bits = words.map((word) => list.indexOf(word).toString(2).padStart(11, '0')).join('');
  const entropy = new Uint8Array(bits.slice(0, ENTROPY_BYTES * 8).match(/.{8}/g)!.map((byte) => parseInt(byte, 2)));
  const expected = await entropyToWords(entropy, list);
  if (expected[PHRASE_WORDS - 1] !== words[PHRASE_WORDS - 1]) {
    return { ok: false, message: 'As palavras existem, mas a combinação não confere. Veja se alguma está trocada ou fora de ordem.' };
  }
  return { ok: true, phrase: words.join(' ') };
}

/** Derivado enviado ao servidor no lugar das palavras (hexadecimal, 128 caracteres). */
export function deriveVerifier(phrase: string): Promise<string> {
  return bip39Seed(phrase, PASSPHRASE);
}

/** Seed do BIP-39: PBKDF2-HMAC-SHA512(frase, "mnemonic" + passphrase, 2048), em hexadecimal. */
export async function bip39Seed(phrase: string, passphrase: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', encoder.encode(phrase.normalize('NFKD')), 'PBKDF2', false, ['deriveBits']);
  const seed = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-512', salt: encoder.encode(`mnemonic${passphrase}`.normalize('NFKD')), iterations: ITERATIONS },
    key,
    512,
  );
  return [...new Uint8Array(seed)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function toBits(bytes: Uint8Array<ArrayBuffer>): string {
  return [...bytes].map((byte) => byte.toString(2).padStart(8, '0')).join('');
}
