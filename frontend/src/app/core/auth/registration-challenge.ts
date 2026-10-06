/**
 * Desafio anti-robô do cadastro (protocolo do ALTCHA, sem biblioteca e sem serviço de terceiros): achar o número que,
 * junto com o salt, dá o SHA-256 do desafio. Para uma pessoa leva cerca de um segundo; para quem cria contas em
 * série, é um custo por conta.
 */
export interface RegistrationChallenge {
  readonly algorithm: string;
  readonly challenge: string;
  readonly salt: string;
  readonly maxNumber: number;
  readonly signature: string;
}

export interface ChallengeSolution {
  readonly challenge: string;
  readonly salt: string;
  readonly number: number;
  readonly signature: string;
}

/** Hashes calculados em paralelo por rodada: cada rodada devolve o controle à tela (o app não trava). */
const BATCH = 500;

export async function solveChallenge(challenge: RegistrationChallenge): Promise<ChallengeSolution> {
  if (challenge.algorithm !== 'SHA-256') {
    throw new Error(`Algoritmo não suportado: ${challenge.algorithm}`);
  }
  const encoder = new TextEncoder();
  for (let start = 0; start <= challenge.maxNumber; start += BATCH) {
    const end = Math.min(start + BATCH - 1, challenge.maxNumber);
    const numbers = Array.from({ length: end - start + 1 }, (_, i) => start + i);
    const hashes = await Promise.all(
      numbers.map((n) => crypto.subtle.digest('SHA-256', encoder.encode(challenge.salt + n))),
    );
    const found = hashes.findIndex((hash) => toHex(hash) === challenge.challenge);
    if (found >= 0) {
      return {
        challenge: challenge.challenge,
        salt: challenge.salt,
        number: numbers[found],
        signature: challenge.signature,
      };
    }
  }
  throw new Error('Desafio sem solução');
}

function toHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer), (byte) => byte.toString(16).padStart(2, '0')).join('');
}
