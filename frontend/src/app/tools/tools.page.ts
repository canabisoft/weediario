import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-tools-page',
  imports: [RouterLink],
  template: `
    <h1 class="text-2xl font-bold">Ferramentas</h1>
    <p class="mt-1 text-fg-muted">Funcionam sem internet: tudo é calculado no próprio celular.</p>

    <ul class="mt-4 grid gap-3">
      @for (tool of tools; track tool.path) {
        <li>
          <a [routerLink]="tool.path" class="wd-card block active:opacity-80">
            <span class="text-lg font-semibold">{{ tool.title }}</span>
            <span class="block text-sm text-fg-muted">{{ tool.description }}</span>
          </a>
        </li>
      }
    </ul>

    <h2 class="mt-8 text-lg font-semibold">Para ler</h2>
    <a routerLink="/guia-de-cultivo" class="wd-card mt-2 block active:opacity-80">
      <span class="text-lg font-semibold">Guia de cultivo em casa</span>
      <span class="block text-sm text-fg-muted"
        >Nove etapas, da lei à cura, com checklist e o que reaproveitar para gastar pouco.</span
      >
    </a>
  `,
})
export class ToolsPage {
  protected readonly tools = [
    {
      path: '/ferramentas/vpd',
      title: 'Calculadora de VPD',
      description:
        'Déficit de pressão de vapor pela temperatura e umidade, com a faixa ideal por fase.',
    },
    {
      path: '/ferramentas/ec-ppm',
      title: 'Conversor EC / PPM',
      description: 'EC, µS/cm, CF e as escalas PPM 500, 640 e 700, com faixas de referência.',
    },
  ] as const;
}
