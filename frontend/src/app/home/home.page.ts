import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { DiaryCard } from '../core/api/api.models';
import { DiariesApi } from '../core/api/diaries.api';
import { AuthService } from '../core/auth/auth.service';
import { DiaryCardComponent } from '../diaries/diary-card';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, DiaryCardComponent],
  template: `
    <section class="relative overflow-hidden rounded-3xl border border-accent/25 bg-linear-to-br from-accent/25 via-surface-raised to-surface p-6 sm:p-8">
      <app-icon name="leaf" class="pointer-events-none absolute -right-6 -top-6 size-40 text-accent/10" [strokeWidth]="1.2" />
      <h1 class="max-w-lg text-2xl font-bold sm:text-3xl">Seu diário de cultivo, com privacidade.</h1>
      <p class="mt-2 max-w-lg text-fg-muted">Sem localização, sem IP e sem metadados nas fotos. Aprenda com a comunidade e registre cada fase da tenda.</p>
      <div class="mt-4 flex flex-wrap gap-2">
        @if (auth.isLoggedIn()) {
          <a routerLink="/diarios/novo" class="wd-button"><app-icon name="plus" class="size-5" /> Iniciar diário</a>
          <a routerLink="/perfil" class="wd-button bg-surface-raised text-fg">Meus diários</a>
        } @else {
          <a routerLink="/cadastro" class="wd-button">Criar conta anônima</a>
          <a routerLink="/entrar" class="wd-button bg-surface-raised text-fg">Entrar</a>
        }
      </div>
    </section>

    <div class="mt-8 flex items-center justify-between">
      <h2 class="text-lg font-semibold">Diários atualizados recentemente</h2>
      <a routerLink="/diarios" class="text-sm font-semibold text-accent">Ver todos →</a>
    </div>
    <ul class="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      @for (diary of latest(); track diary.id) {
        <li><app-diary-card [diary]="diary" /></li>
      } @empty {
        <li class="wd-card text-center text-fg-muted sm:col-span-2 lg:col-span-3">Os primeiros diários da comunidade aparecem aqui.</li>
      }
    </ul>

    <h2 class="mb-2 mt-8 text-lg font-semibold">Ferramentas (funcionam offline)</h2>
    <div class="grid gap-3 sm:grid-cols-2">
      <a routerLink="/ferramentas/vpd" class="wd-card block hover:border-accent/50">
        <span class="font-semibold">Calculadora de VPD</span>
        <span class="block text-sm text-fg-muted">Temperatura + umidade: sua planta está na zona ideal?</span>
      </a>
      <a routerLink="/ferramentas/ec-ppm" class="wd-card block hover:border-accent/50">
        <span class="font-semibold">Conversor EC / PPM</span>
        <span class="block text-sm text-fg-muted">Converta entre as escalas dos medidores.</span>
      </a>
    </div>
  `,
})
export class HomePage {
  protected readonly auth = inject(AuthService);
  protected readonly latest = toSignal(
    inject(DiariesApi)
      .feed({ size: 6 })
      .pipe(
        map((page) => page.items),
        catchError(() => of<DiaryCard[]>([])),
      ),
    { initialValue: [] },
  );
}
