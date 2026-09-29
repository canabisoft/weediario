import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { DiaryCard, GrowDiaryView } from '../../core/api/api.models';
import { DiariesApi } from '../../core/api/diaries.api';
import { problemMessage } from '../../core/api/problem';
import { Icon } from '../../shared/icon';
import { today } from '../../shared/labels';

type Mode = 'idle' | 'complete' | 'harvest';

/**
 * Ações do dono: novo update, visibilidade, concluir (com colheita), registrar colheita depois e cancelar.
 */
@Component({
  selector: 'app-diary-owner-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Icon],
  template: `
    @let d = diary();
    <section class="wd-card grid gap-3" aria-label="Gerenciar diário">
      <div class="flex flex-wrap gap-2">
        @if (d.status === 'IN_PROGRESS') {
          <a [routerLink]="['/diarios', d.id, 'update']" class="wd-button">
            <app-icon name="camera" class="size-5" /> Novo update
          </a>
        }
        <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="toggleVisibility()">
          <app-icon [name]="d.visibility === 'PUBLIC' ? 'eyeOff' : 'eye'" class="size-5" />
          {{ d.visibility === 'PUBLIC' ? 'Tornar privado' : 'Tornar público' }}
        </button>
        @if (d.status === 'IN_PROGRESS') {
          <button type="button" class="wd-button bg-surface-raised text-fg" (click)="mode.set('complete')">
            <app-icon name="scissors" class="size-5" /> Concluir (colheita)
          </button>
          <button type="button" class="wd-button bg-surface-raised text-danger" [disabled]="busy()" (click)="cancel()">
            Cancelar cultivo
          </button>
        }
        @if (d.status === 'COMPLETED' && !d.harvest) {
          <button type="button" class="wd-button bg-surface-raised text-fg" (click)="mode.set('harvest')">
            <app-icon name="scale" class="size-5" /> Registrar peso seco
          </button>
        }
      </div>

      @if (mode() !== 'idle') {
        <form class="grid gap-3 rounded-xl border border-line p-3 sm:grid-cols-3" [formGroup]="form" (ngSubmit)="submit()">
          @if (mode() === 'complete') {
            <div>
              <label class="wd-label" for="harvested-on">Data da colheita</label>
              <input id="harvested-on" type="date" class="wd-input text-base" formControlName="harvestedOn" [max]="todayIso" />
            </div>
          }
          <div>
            <label class="wd-label" for="dry-grams">Peso seco total (g)</label>
            <input id="dry-grams" type="number" inputmode="numeric" min="1" max="100000" class="wd-input text-base" formControlName="dryGrams" />
          </div>
          <div>
            <label class="wd-label" for="plants">Nº de plantas</label>
            <input id="plants" type="number" inputmode="numeric" min="1" max="500" class="wd-input text-base" formControlName="plants" />
          </div>
          <p class="text-xs text-fg-muted sm:col-span-3">
            @if (mode() === 'complete') {
              O peso é opcional agora: dá para registrar depois da secagem e cura. Ele alimenta o "g/planta" do ranking.
            } @else {
              Peso seco depois da cura, somando todas as plantas colhidas.
            }
          </p>
          <div class="flex gap-2 sm:col-span-3">
            <button type="submit" class="wd-button" [disabled]="busy()">Salvar</button>
            <button type="button" class="wd-button bg-surface-raised text-fg" (click)="mode.set('idle')">Voltar</button>
          </div>
        </form>
      }

      @if (error()) {
        <p role="alert" class="text-sm text-danger">{{ error() }}</p>
      }
    </section>
  `,
})
export class DiaryOwnerPanel {
  private readonly api = inject(DiariesApi);

  readonly diary = input.required<DiaryCard>();
  readonly changed = output<void>();

  protected readonly todayIso = today();
  protected readonly mode = signal<Mode>('idle');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).group({
    harvestedOn: [today()],
    dryGrams: [null as number | null, [Validators.min(1), Validators.max(100000)]],
    plants: [null as number | null, [Validators.min(1), Validators.max(500)]],
  });

  protected toggleVisibility(): void {
    const next = this.diary().visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    this.run(this.api.changeVisibility(this.diary().id, next));
  }

  protected cancel(): void {
    if (confirm('Cancelar este cultivo? Ele deixa de receber updates.')) {
      this.run(this.api.cancel(this.diary().id));
    }
  }

  protected submit(): void {
    const { harvestedOn, dryGrams, plants } = this.form.getRawValue();
    if (this.mode() === 'harvest') {
      if (!dryGrams || !plants) {
        this.error.set('Informe o peso seco e o número de plantas.');
        return;
      }
      this.run(this.api.recordHarvest(this.diary().id, dryGrams, plants));
    } else {
      this.run(this.api.complete(this.diary().id, harvestedOn ?? undefined, dryGrams ?? undefined, plants ?? undefined));
    }
  }

  private run(request: Observable<GrowDiaryView>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.mode.set('idle');
        this.changed.emit();
      },
      error: (error) => {
        this.error.set(problemMessage(error));
        this.busy.set(false);
      },
      complete: () => this.busy.set(false),
    });
  }
}
