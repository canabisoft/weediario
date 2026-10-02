import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { CatalogDbApi, TestingStrainItem } from '../core/api/catalog-db.api';
import { DOMINANCE_LABEL } from '../shared/labels';
import { TestingProgressBar } from '../shared/testing-progress';

/**
 * Sementes do breeder ainda em teste pela comunidade, com o andamento de cada uma. Some quando não há nenhuma.
 */
@Component({
  selector: 'app-testing-strains',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, TestingProgressBar],
  template: `
    @if (strains(); as list) {
      @if (list.length) {
        <section class="mt-6" aria-labelledby="testing-strains-title">
          <h2 id="testing-strains-title" class="text-lg font-semibold">Sementes em teste</h2>
          <p class="text-sm text-fg-muted">
            Cadastradas pelo próprio breeder. Entram no catálogo oficial quando 3 cultivadores de fora do breeder as cultivam
            e uma colheita é avaliada.
          </p>
          <ul class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (strain of list; track strain.id) {
              <li>
                <a [routerLink]="['/strains', strain.id]" class="wd-card grid gap-2 hover:border-accent/50">
                  <span>
                    <span class="block truncate font-semibold">{{ strain.name }}</span>
                    <span class="block truncate text-xs text-fg-muted">{{ traits(strain) || 'Sem informações ainda' }}</span>
                  </span>
                  @if (strain.progress; as progress) {
                    <app-testing-progress [progress]="progress" />
                  }
                </a>
              </li>
            }
          </ul>
        </section>
      }
    }
  `,
})
export class TestingStrains {
  private readonly api = inject(CatalogDbApi);

  readonly breederId = input.required<string>();

  protected readonly strains = toSignal(
    toObservable(this.breederId).pipe(
      switchMap((id) =>
        this.api.testingStrains('', id, 0, 50).pipe(
          map((page) => page.items),
          catchError(() => of([] as TestingStrainItem[])),
        ),
      ),
    ),
  );

  protected traits(strain: TestingStrainItem): string {
    return [
      strain.dominance ? DOMINANCE_LABEL[strain.dominance] : null,
      strain.floweringType === 'AUTOFLOWER' ? 'automática' : null,
      strain.genetics,
    ]
      .filter(Boolean)
      .join(' · ');
  }
}
