import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api/catalog.api';
import { problemMessage } from '../../core/api/problem';
import { StarInput } from '../../shared/rating';

/**
 * Review da genética (e, se o diário informa o banco, do seedbank), feita pelo autor depois de concluir o cultivo.
 */
@Component({
  selector: 'app-review-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, StarInput],
  template: `
    @if (sentStrainId(); as strainId) {
      <section class="wd-card">
        <p class="font-semibold text-accent">Obrigado! Sua avaliação foi publicada.</p>
        <a [routerLink]="['/strains', strainId]" class="text-sm underline">Ver a strain no catálogo</a>
      </section>
    } @else {
      <form class="wd-card grid gap-4" (ngSubmit)="submit()">
        <div>
          <h2 class="text-lg font-semibold">Avaliar {{ strain() }}</h2>
          <p class="text-sm text-fg-muted">Sua avaliação entra na média da comunidade para esta genética.</p>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <app-star-input label="Vigor vegetativo" name="vigor" [(value)]="vigor" />
          <app-star-input label="Resistência a pragas/estresse" name="pest" [(value)]="pestResistance" />
          <app-star-input label="Facilidade de cultivo" name="ease" [(value)]="easeOfGrowth" />
          <app-star-input label="Rendimento" name="yield" [(value)]="yieldRating" />
          <app-star-input label="Aroma e sabor" name="terpenes" [(value)]="terpeneProfile" />
        </div>

        @if (breeder()) {
          <fieldset class="grid gap-3 rounded-xl border border-line p-3">
            <legend class="px-1 font-semibold">Banco: {{ breeder() }} <span class="text-sm font-normal text-fg-muted">(opcional)</span></legend>
            <div class="grid gap-3 sm:grid-cols-3">
              <app-star-input label="Germinação" name="germination" [(value)]="germination" />
              <app-star-input label="Estabilidade genética" name="stability" [(value)]="stability" />
              <app-star-input label="Atendimento e entrega" name="service" [(value)]="service" />
            </div>
          </fieldset>
        }

        <div>
          <label class="wd-label" for="report">Grow/smoke report</label>
          <textarea
            id="report"
            rows="5"
            minlength="20"
            maxlength="10000"
            class="wd-input py-2 text-base"
            placeholder="Como foi o cultivo, o tempo de flora, o aroma, o efeito…"
            [(ngModel)]="report"
            name="report"
          ></textarea>
        </div>

        @if (error()) {
          <p role="alert" class="text-sm text-danger">{{ error() }}</p>
        }
        <button type="submit" class="wd-button" [disabled]="!canSubmit() || busy()">Publicar avaliação</button>
      </form>
    }
  `,
})
export class ReviewForm {
  private readonly api = inject(CatalogApi);

  readonly diaryId = input.required<string>();
  readonly strain = input.required<string>();
  readonly breeder = input<string | undefined>();

  protected readonly vigor = signal<number | null>(null);
  protected readonly pestResistance = signal<number | null>(null);
  protected readonly easeOfGrowth = signal<number | null>(null);
  protected readonly yieldRating = signal<number | null>(null);
  protected readonly terpeneProfile = signal<number | null>(null);
  protected readonly germination = signal<number | null>(null);
  protected readonly stability = signal<number | null>(null);
  protected readonly service = signal<number | null>(null);
  protected report = '';

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sentStrainId = signal<string | null>(null);

  private readonly strainRated = computed(() =>
    [this.vigor(), this.pestResistance(), this.easeOfGrowth(), this.yieldRating(), this.terpeneProfile()].every(
      (value) => value !== null,
    ),
  );

  protected canSubmit(): boolean {
    return this.strainRated() && this.report.trim().length >= 20;
  }

  protected submit(): void {
    const seedbank = [this.germination(), this.stability(), this.service()];
    const seedbankRated = seedbank.filter((value) => value !== null).length;
    if (seedbankRated > 0 && seedbankRated < 3) {
      this.error.set('Para avaliar o banco, dê nota aos três critérios (ou deixe os três em branco).');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .submitReview(this.diaryId(), {
        vigor: this.vigor()!,
        pestResistance: this.pestResistance()!,
        easeOfGrowth: this.easeOfGrowth()!,
        yield: this.yieldRating()!,
        terpeneProfile: this.terpeneProfile()!,
        seedbankGermination: this.germination() ?? undefined,
        seedbankStability: this.stability() ?? undefined,
        seedbankService: this.service() ?? undefined,
        report: this.report.trim(),
      })
      .subscribe({
        next: (result) => this.sentStrainId.set(result.strainId),
        error: (error) => {
          this.error.set(problemMessage(error));
          this.busy.set(false);
        },
      });
  }
}
