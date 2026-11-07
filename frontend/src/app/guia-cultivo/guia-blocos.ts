import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Bloco } from './guia-cultivo.data';
import { GuiaLojas } from './guia-lojas';
import { GuiaTexto } from './guia-texto';

/** Desenha os blocos de conteúdo de uma etapa. Recursivo: uma "caixa" tem blocos dentro. */
@Component({
  selector: 'app-guia-blocos',
  imports: [GuiaTexto, GuiaLojas],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (b of blocos(); track $index) {
      @switch (b.t) {
        @case ('p') {
          <p class="mt-3 leading-relaxed"><app-guia-texto [texto]="b.x" /></p>
        }
        @case ('h') {
          <h3 class="mt-6 text-lg font-bold"><app-guia-texto [texto]="b.x" /></h3>
        }
        @case ('ul') {
          <ul class="mt-3 list-disc space-y-1.5 pl-5">
            @for (i of b.itens; track $index) {
              <li><app-guia-texto [texto]="i" /></li>
            }
          </ul>
        }
        @case ('ol') {
          <ol class="mt-3 list-decimal space-y-1.5 pl-5 marker:font-bold marker:text-accent">
            @for (i of b.itens; track $index) {
              <li><app-guia-texto [texto]="i" /></li>
            }
          </ol>
        }
        @case ('tabela') {
          <div class="mt-3 overflow-x-auto rounded-xl border border-line">
            <table class="w-full min-w-[32rem] border-collapse text-left text-sm">
              <thead class="bg-surface-raised">
                <tr>
                  @for (c of b.cab; track $index) {
                    <th scope="col" class="p-3 font-semibold">{{ c }}</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (linha of b.linhas; track $index) {
                  <tr class="border-t border-line align-top">
                    @for (c of linha; track $index) {
                      @if ($first) {
                        <th scope="row" class="p-3 font-semibold text-fg-muted">
                          <app-guia-texto [texto]="c" />
                        </th>
                      } @else {
                        <td class="p-3"><app-guia-texto [texto]="c" /></td>
                      }
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
        @case ('nota') {
          <p
            class="mt-4 rounded-xl p-3 text-sm"
            [class.bg-accent/10]="b.tipo === 'gastando'"
            [class.bg-danger/10]="b.tipo === 'erro'"
            [class.bg-surface-raised]="b.tipo === 'aviso'"
            [class.text-fg-muted]="b.tipo === 'info'"
          >
            @if (b.tipo === 'gastando') {
              <strong>Gastando pouco: </strong>
            } @else if (b.tipo === 'erro') {
              <strong>Erro comum: </strong>
            }
            <app-guia-texto [texto]="b.x" />
          </p>
        }
        @case ('caixa') {
          <section class="mt-4 rounded-2xl border border-warn/40 bg-warn/10 p-4">
            <h3 class="font-bold">{{ b.titulo }}</h3>
            <app-guia-blocos [blocos]="b.blocos" />
          </section>
        }
        @case ('receita') {
          <details class="mt-3 rounded-xl border border-line bg-surface px-3 py-2">
            <summary class="cursor-pointer font-semibold">{{ b.titulo }}</summary>
            <ul class="mt-2 list-disc space-y-1 pl-5">
              @for (i of b.itens; track $index) {
                <li><app-guia-texto [texto]="i" /></li>
              }
            </ul>
          </details>
        }
        @case ('link') {
          <p class="mt-3">
            <a
              [href]="b.href"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex min-h-11 items-center rounded-xl border border-accent px-4 font-semibold text-accent hover:bg-accent/10"
              >{{ b.x }}</a
            >
          </p>
        }
        @case ('lojas') {
          <app-guia-lojas [itens]="b.itens" />
        }
      }
    }
  `,
})
export class GuiaBlocos {
  readonly blocos = input.required<readonly Bloco[]>();
}
