import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  template: `
    <section class="wd-card">
      <h1 class="text-2xl font-bold">Seu diário de cultivo, com privacidade.</h1>
      <p class="mt-2 text-fg-muted">
        Sem localização, sem IP e sem metadados nas fotos. O feed da comunidade e os diários chegam na próxima
        versão do app.
      </p>
    </section>

    <h2 class="mb-2 mt-6 text-sm font-semibold uppercase tracking-wide text-fg-muted">Já disponível</h2>
    <div class="grid gap-3">
      <a routerLink="/ferramentas/vpd" class="wd-card block active:opacity-80">
        <span class="font-semibold">Calculadora de VPD</span>
        <span class="block text-sm text-fg-muted">Temperatura + umidade: sua planta está na zona ideal?</span>
      </a>
      <a routerLink="/ferramentas/ec-ppm" class="wd-card block active:opacity-80">
        <span class="font-semibold">Conversor EC / PPM</span>
        <span class="block text-sm text-fg-muted">Converta entre as escalas dos medidores.</span>
      </a>
    </div>
  `,
})
export class HomePage {}
