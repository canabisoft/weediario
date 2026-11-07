import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LOJAS, Loja, PRECOS_EM, ROTULOS_LOJAS } from './guia-cultivo.data';

interface Grupo {
  readonly chave: string;
  readonly rotulo: string;
  readonly lojas: readonly Loja[];
}

/**
 * Onde encontrar o item, com preço quando a loja mostra. São links diretos para as lojas, sem redirecionador e sem
 * contagem de cliques: o weediário não usa analytics e não recebe comissão. O preço vale para a data de PRECOS_EM (Pix)
 * e muda; o texto abaixo da lista avisa.
 */
@Component({
  selector: 'app-guia-lojas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (g of grupos(); track g.chave) {
      <div class="mt-3">
        <h4 class="text-sm font-semibold">{{ g.rotulo }}</h4>
        <ul class="mt-1 grid gap-2">
          @for (l of g.lojas; track l.url) {
            <li>
              <a
                [href]="l.url"
                target="_blank"
                rel="noopener noreferrer"
                class="flex items-center justify-between gap-3 rounded-xl border border-line bg-surface p-3 hover:border-accent/60"
              >
                <span class="min-w-0">
                  <span class="block text-xs font-bold text-accent">{{ l.loja }}</span>
                  <span class="block text-sm">{{ l.nome }}</span>
                </span>
                <span class="shrink-0 text-right text-sm">
                  @if (l.preco) {
                    <strong class="block">{{ l.preco }}</strong>
                    <span class="block text-xs text-fg-muted">{{ l.nota ?? 'no Pix' }}</span>
                  } @else {
                    <span class="text-xs text-fg-muted">ver preço na loja</span>
                  }
                </span>
              </a>
            </li>
          }
        </ul>
      </div>
    }
    @if (grupos().length) {
      <p class="mt-2 text-xs text-fg-muted">
        Preços conferidos em {{ precosEm }}, no Pix, e podem mudar. O weediário não vende e não
        recebe comissão destas lojas.
      </p>
    }
  `,
})
export class GuiaLojas {
  readonly itens = input.required<readonly string[]>();
  protected readonly precosEm = PRECOS_EM;

  protected readonly grupos = computed<Grupo[]>(() =>
    this.itens()
      .filter((chave) => (LOJAS[chave]?.length ?? 0) > 0)
      .map((chave) => ({ chave, rotulo: ROTULOS_LOJAS[chave] ?? chave, lojas: LOJAS[chave] })),
  );
}
