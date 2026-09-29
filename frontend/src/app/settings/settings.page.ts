import { Component, inject } from '@angular/core';
import { THEMES, ThemeService } from '../core/theme/theme.service';

@Component({
  selector: 'app-settings-page',
  template: `
    <h1 class="text-2xl font-bold">Ajustes</h1>

    <fieldset class="wd-card mt-4">
      <legend class="px-1 text-lg font-semibold">Tema</legend>
      <div class="mt-2 grid gap-2">
        @for (option of themes; track option.id) {
          <label
            class="flex min-h-14 cursor-pointer items-start gap-3 rounded-xl border border-line p-3"
            [class.border-accent]="theme.theme() === option.id"
          >
            <input
              type="radio"
              name="theme"
              class="mt-1 size-5 accent-(--wd-accent)"
              [value]="option.id"
              [checked]="theme.theme() === option.id"
              (change)="theme.select(option.id)"
            />
            <span>
              <span class="block font-medium">{{ option.label }}</span>
              <span class="block text-sm text-fg-muted">{{ option.hint }}</span>
            </span>
          </label>
        }
      </div>
    </fieldset>

    <section class="wd-card mt-4 text-sm text-fg-muted">
      <h2 class="mb-1 font-semibold text-fg">Sobre o modo noturno de cultivo</h2>
      <p>
        Durante o período de escuro, luz branca ou azul pode estressar as plantas. O modo noturno usa só verde escuro
        sobre preto e escurece as fotos. Para o efeito completo, baixe também o brilho da tela ao mínimo.
      </p>
    </section>
  `,
})
export class SettingsPage {
  protected readonly theme = inject(ThemeService);
  protected readonly themes = THEMES;
}
