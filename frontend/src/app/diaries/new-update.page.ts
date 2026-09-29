import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { GrowthStage } from '../core/api/api.models';
import { AccountApi } from '../core/api/account.api';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { formatDecimal, parseDecimal } from '../shared/decimal';
import { Icon } from '../shared/icon';
import { STAGE_LABEL, options, today } from '../shared/labels';
import { VPD_STAGES, calculateVpd, classify } from '../tools/vpd/vpd';

interface PendingPhoto {
  readonly key: number;
  /** Prévia local (blob:) só neste aparelho; o arquivo enviado é sanitizado pelo servidor. */
  readonly preview: string;
  progress: number;
  imageId?: string;
  error?: string;
  subscription?: Subscription;
}

const MAX_PHOTOS = 10;

/**
 * Novo update, pensado para usar dentro da tenda: fase em botões grandes, câmera direto, upload em paralelo
 * com progresso e o VPD calculado na hora a partir da temperatura e umidade digitadas.
 */
@Component({
  selector: 'app-new-update-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon],
  template: `
    <section class="mx-auto max-w-xl">
      <a [routerLink]="['/diarios', id()]" class="text-sm text-fg-muted">← Voltar ao diário</a>
      <h1 class="mt-1 text-2xl font-bold">Novo update</h1>

      <form class="mt-4 grid gap-5" (ngSubmit)="publish()">
        <fieldset>
          <legend class="wd-label">Fase atual</legend>
          <div class="grid grid-cols-2 gap-2">
            @for (o of stageOptions; track o.value) {
              <label
                class="flex min-h-14 cursor-pointer items-center justify-center rounded-xl border text-center font-semibold"
                [class]="stage() === o.value ? 'border-accent bg-accent/15 text-accent' : 'border-line'"
              >
                <input type="radio" name="stage" class="sr-only" [value]="o.value" [checked]="stage() === o.value" (change)="stage.set(o.value)" />
                {{ o.label }}
              </label>
            }
          </div>
        </fieldset>

        <div>
          <p class="wd-label">Fotos ({{ photos().length }}/{{ maxPhotos }})</p>
          <div class="grid grid-cols-2 gap-2">
            <label class="wd-button cursor-pointer" [class.opacity-50]="photos().length >= maxPhotos">
              <app-icon name="camera" class="size-5" /> Tirar foto
              <input type="file" accept="image/*" capture="environment" class="sr-only" [disabled]="photos().length >= maxPhotos" (change)="addFiles($event)" />
            </label>
            <label class="wd-button cursor-pointer bg-surface-raised text-fg" [class.opacity-50]="photos().length >= maxPhotos">
              <app-icon name="image" class="size-5" /> Galeria
              <input type="file" accept="image/jpeg,image/png,image/webp" multiple class="sr-only" [disabled]="photos().length >= maxPhotos" (change)="addFiles($event)" />
            </label>
          </div>
          <p class="mt-1 text-xs text-fg-muted">GPS, modelo do celular e data da foto são removidos no servidor antes de publicar.</p>

          @if (photos().length) {
            <ul class="mt-3 grid grid-cols-3 gap-2">
              @for (photo of photos(); track photo.key) {
                <li class="relative aspect-square overflow-hidden rounded-xl bg-surface-raised">
                  <img [src]="photo.preview" alt="" class="size-full object-cover" [class.opacity-40]="!photo.imageId" />
                  @if (photo.error) {
                    <span class="absolute inset-x-0 bottom-0 bg-black/70 p-1 text-center text-[11px] text-white">{{ photo.error }}</span>
                  } @else if (!photo.imageId) {
                    <span class="absolute inset-x-2 bottom-2 h-1.5 overflow-hidden rounded-full bg-black/40" role="progressbar" [attr.aria-valuenow]="photo.progress" aria-valuemin="0" aria-valuemax="100" aria-label="Enviando foto">
                      <span class="block h-full bg-accent transition-all" [style.width.%]="photo.progress"></span>
                    </span>
                  }
                  <button type="button" class="absolute right-1 top-1 grid size-8 place-items-center rounded-full bg-black/60 text-white" aria-label="Remover foto" (click)="removePhoto(photo)">
                    <app-icon name="close" class="size-4" />
                  </button>
                </li>
              }
            </ul>
          }
        </div>

        <div>
          <label class="wd-label" for="note">Anotação</label>
          <textarea id="note" rows="4" maxlength="5000" class="wd-input py-2 text-base" name="note" [(ngModel)]="note" placeholder="Rega, poda, sinais da planta…"></textarea>
        </div>

        <details class="wd-card" [open]="hasMetrics()">
          <summary class="cursor-pointer font-semibold">Métricas (opcional)</summary>
          <div class="mt-3 grid grid-cols-2 gap-3">
            <div>
              <label class="wd-label" for="temp">Temperatura (°C)</label>
              <input id="temp" inputmode="decimal" class="wd-input" [value]="temperature()" (input)="temperature.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" for="rh">Umidade (%)</label>
              <input id="rh" inputmode="decimal" class="wd-input" [value]="humidity()" (input)="humidity.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" for="ph">pH da rega</label>
              <input id="ph" inputmode="decimal" class="wd-input" [value]="ph()" (input)="ph.set($any($event.target).value)" />
            </div>
            <div>
              <label class="wd-label" for="height">Altura (cm)</label>
              <input id="height" inputmode="decimal" class="wd-input" [value]="height()" (input)="height.set($any($event.target).value)" />
            </div>
          </div>
          @if (vpd(); as v) {
            <p class="mt-3 rounded-xl bg-surface-raised p-3 text-sm" aria-live="polite">
              VPD da folha: <strong>{{ v.value }} kPa</strong> · {{ v.label }}
            </p>
          }
        </details>

        <div>
          <label class="wd-label" for="recorded-on">Data do update</label>
          <input id="recorded-on" type="date" class="wd-input" name="recordedOn" [(ngModel)]="recordedOn" [max]="todayIso" />
        </div>

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }
        <button type="submit" class="wd-button min-h-14 text-lg" [disabled]="busy() || uploading()">
          {{ uploading() ? 'Enviando fotos…' : busy() ? 'Publicando…' : 'Publicar update' }}
        </button>
      </form>
    </section>
  `,
})
export class NewUpdatePage {
  private readonly diaries = inject(DiariesApi);
  private readonly account = inject(AccountApi);
  private readonly router = inject(Router);

  /** Parâmetro de rota. */
  readonly id = input.required<string>();

  protected readonly maxPhotos = MAX_PHOTOS;
  protected readonly todayIso = today();
  protected readonly stageOptions = options(STAGE_LABEL);
  protected readonly stage = signal<GrowthStage>('VEGETATIVE');
  protected readonly photos = signal<PendingPhoto[]>([]);
  protected readonly temperature = signal('');
  protected readonly humidity = signal('');
  protected readonly ph = signal('');
  protected readonly height = signal('');
  protected note = '';
  protected recordedOn = today();
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private nextKey = 0;

  protected readonly uploading = computed(() => this.photos().some((photo) => !photo.imageId && !photo.error));
  protected readonly hasMetrics = computed(() => [this.temperature(), this.humidity(), this.ph(), this.height()].some(Boolean));

  protected readonly vpd = computed(() => {
    const air = parseDecimal(this.temperature());
    const rh = parseDecimal(this.humidity());
    if (air === null || rh === null) {
      return null;
    }
    try {
      const leafVpd = calculateVpd({ airTempC: air, relativeHumidity: rh }).leafVpd;
      const range = VPD_STAGES.find((s) => s.id === vpdStageFor(this.stage()))!.range;
      const zone = classify(leafVpd, range);
      return { value: formatDecimal(leafVpd, 2), label: { low: 'abaixo do ideal', ideal: 'na faixa ideal', high: 'acima do ideal' }[zone] };
    } catch {
      return null;
    }
  });

  constructor() {
    inject(DestroyRef).onDestroy(() =>
      this.photos().forEach((photo) => {
        photo.subscription?.unsubscribe();
        URL.revokeObjectURL(photo.preview);
      }),
    );
  }

  protected addFiles(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    const files = Array.from(inputElement.files ?? []).slice(0, MAX_PHOTOS - this.photos().length);
    inputElement.value = '';
    for (const file of files) {
      const photo: PendingPhoto = { key: this.nextKey++, preview: URL.createObjectURL(file), progress: 0 };
      this.photos.update((list) => [...list, photo]);
      photo.subscription = this.account.uploadImage(file).subscribe({
        next: (event) => {
          photo.progress = event.progress;
          if ('image' in event) {
            photo.imageId = event.image.id;
          }
          this.photos.update((list) => [...list]);
        },
        error: (error) => {
          photo.error = problemMessage(error, 'Falha no envio');
          this.photos.update((list) => [...list]);
        },
      });
    }
  }

  protected removePhoto(photo: PendingPhoto): void {
    photo.subscription?.unsubscribe();
    URL.revokeObjectURL(photo.preview);
    this.photos.update((list) => list.filter((candidate) => candidate !== photo));
  }

  protected publish(): void {
    this.busy.set(true);
    this.error.set(null);
    this.diaries
      .publishUpdate(this.id(), {
        stage: this.stage(),
        recordedOn: this.recordedOn || undefined,
        note: this.note.trim() || undefined,
        temperatureCelsius: parseDecimal(this.temperature()) ?? undefined,
        relativeHumidity: parseDecimal(this.humidity()) ?? undefined,
        wateringPh: parseDecimal(this.ph()) ?? undefined,
        plantHeightCm: parseDecimal(this.height()) ?? undefined,
        photoIds: this.photos().flatMap((photo) => (photo.imageId ? [photo.imageId] : [])),
      })
      .subscribe({
        next: () => this.router.navigate(['/diarios', this.id()]),
        error: (error) => {
          this.error.set(problemMessage(error));
          this.busy.set(false);
        },
      });
  }
}

function vpdStageFor(stage: GrowthStage) {
  return stage === 'GERMINATION' ? 'propagation' : stage === 'VEGETATIVE' ? 'vegetative' : 'early-flower';
}
