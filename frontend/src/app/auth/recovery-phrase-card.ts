import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { deriveVerifier, generatePhrase } from '../core/auth/recovery-phrase';

const CHECKED_POSITIONS = 3;

/**
 * Mostra uma frase de recuperação nova (gerada no navegador) e só libera depois que a pessoa confirma algumas
 * palavras, para garantir que anotou. Emite o derivado da frase; as palavras nunca saem deste componente.
 */
@Component({
  selector: 'app-recovery-phrase-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="grid gap-4">
      <div role="alert" class="rounded-xl border border-warn/50 bg-warn/10 px-3 py-2 text-sm">
        <p class="font-semibold">Anote estas 12 palavras, na ordem, em papel.</p>
        <p class="mt-1">
          O weediário não tem e-mail nem qualquer outro jeito de recuperar sua conta: se você perder a chave de acesso e
          as palavras, a conta está perdida de vez. Ninguém da equipe consegue ver ou reenviar a frase — ela só existe
          aqui, neste aparelho, agora.
        </p>
      </div>

      @if (words(); as list) {
        <ol class="grid grid-cols-2 gap-2 sm:grid-cols-3" aria-label="Frase de recuperação">
          @for (word of list; track $index) {
            <li class="flex items-center gap-2 rounded-xl bg-surface-raised px-3 py-2 font-mono text-base">
              <span class="w-5 text-right text-xs text-fg-muted">{{ $index + 1 }}</span>
              <span class="font-semibold">{{ word }}</span>
            </li>
          }
        </ol>
        <button type="button" class="justify-self-start text-sm text-fg-muted underline" (click)="regenerate()">
          Gerar outra frase
        </button>

        <fieldset class="grid gap-3 rounded-xl border border-line p-3">
          <legend class="px-1 text-sm font-semibold">Confirme que anotou</legend>
          @for (position of positions(); track position) {
            <div>
              <label class="wd-label" [for]="'word-' + position">{{ position + 1 }}ª palavra</label>
              <input
                class="wd-input font-mono"
                [id]="'word-' + position"
                [name]="'word-' + position"
                autocomplete="off"
                autocapitalize="none"
                spellcheck="false"
                [ngModel]="answers()[position] ?? ''"
                (ngModelChange)="answer(position, $event)"
              />
            </div>
          }
          <label class="flex items-start gap-2 text-sm">
            <input type="checkbox" class="mt-0.5 size-5" [ngModel]="saved()" (ngModelChange)="saved.set($event)" name="saved" />
            <span>Guardei as palavras num lugar seguro e entendo que, sem elas, não dá para recuperar a conta.</span>
          </label>
        </fieldset>

        <button type="button" class="wd-button" [disabled]="!confirmed() || busy() || disabled()" (click)="confirm()">
          {{ busy() ? 'Preparando…' : confirmLabel() }}
        </button>
      } @else {
        <div class="h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
      }
    </div>
  `,
})
export class RecoveryPhraseCard {
  readonly confirmLabel = input('Continuar');
  /** Trava o botão enquanto o pai ainda não pode enviar (ex.: falta a chave de acesso atual). */
  readonly disabled = input(false);
  /** Derivado da frase (PBKDF2), pronto para enviar ao servidor. */
  readonly verified = output<string>();

  protected readonly words = signal<string[] | null>(null);
  protected readonly positions = signal<number[]>([]);
  protected readonly answers = signal<Record<number, string>>({});
  protected readonly saved = signal(false);
  protected readonly busy = signal(false);
  protected readonly confirmed = computed(() => {
    const list = this.words();
    const answers = this.answers();
    return (
      this.saved() &&
      !!list &&
      this.positions().every((position) => (answers[position] ?? '').trim().toLowerCase() === list[position])
    );
  });

  constructor() {
    void this.regenerate();
  }

  protected async regenerate(): Promise<void> {
    const list = await generatePhrase();
    this.words.set(list);
    this.positions.set(pickPositions(list.length));
    this.answers.set({});
    this.saved.set(false);
  }

  protected answer(position: number, value: string): void {
    this.answers.update((answers) => ({ ...answers, [position]: value }));
  }

  protected async confirm(): Promise<void> {
    const list = this.words();
    if (!list || !this.confirmed()) {
      return;
    }
    this.busy.set(true);
    try {
      this.verified.emit(await deriveVerifier(list.join(' ')));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Posições sorteadas (com o gerador criptográfico) e em ordem crescente. */
function pickPositions(total: number): number[] {
  const picked = new Set<number>();
  const random = new Uint32Array(1);
  while (picked.size < CHECKED_POSITIONS) {
    crypto.getRandomValues(random);
    picked.add(random[0] % total);
  }
  return [...picked].sort((a, b) => a - b);
}
