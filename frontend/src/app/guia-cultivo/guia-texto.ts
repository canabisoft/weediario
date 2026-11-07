import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface Trecho {
  readonly texto: string;
  readonly forte?: boolean;
  readonly italico?: boolean;
  readonly href?: string;
  readonly interno?: boolean;
}

const MARCACAO = /\[([^\]]+)\]\(([^)\s]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*/g;

/**
 * Texto do guia com marcação mínima: **negrito**, *itálico* e [texto](link). Só viram link os endereços https://
 * (externos) e os que começam com "/" (internos); qualquer outra coisa fica como texto puro. Nada é HTML, então não há
 * o que sanear nem como escapar da CSP.
 */
export function trechos(texto: string): Trecho[] {
  const saida: Trecho[] = [];
  let pos = 0;
  for (const m of texto.matchAll(MARCACAO)) {
    if (m.index > pos) {
      saida.push({ texto: texto.slice(pos, m.index) });
    }
    if (m[1] !== undefined) {
      const href = m[2];
      if (href.startsWith('https://')) {
        saida.push({ texto: m[1], href });
      } else if (href.startsWith('/') && !href.startsWith('//')) {
        saida.push({ texto: m[1], href, interno: true });
      } else {
        saida.push({ texto: m[1] });
      }
    } else if (m[3] !== undefined) {
      saida.push({ texto: m[3], forte: true });
    } else {
      saida.push({ texto: m[4], italico: true });
    }
    pos = m.index + m[0].length;
  }
  if (pos < texto.length) {
    saida.push({ texto: texto.slice(pos) });
  }
  return saida;
}

@Component({
  selector: 'app-guia-texto',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (t of partes(); track $index) {
      @if (t.href && t.interno) {
        <a [routerLink]="t.href" class="font-semibold text-accent underline">{{ t.texto }}</a>
      } @else if (t.href) {
        <a
          [href]="t.href"
          target="_blank"
          rel="noopener noreferrer"
          class="font-semibold text-accent underline"
          >{{ t.texto }}</a
        >
      } @else if (t.forte) {
        <strong>{{ t.texto }}</strong>
      } @else if (t.italico) {
        <em>{{ t.texto }}</em>
      } @else {
        <ng-container>{{ t.texto }}</ng-container>
      }
    }
  `,
})
export class GuiaTexto {
  readonly texto = input.required<string>();
  protected readonly partes = computed(() => trechos(this.texto()));
}
