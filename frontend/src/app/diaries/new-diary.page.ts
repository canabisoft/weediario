import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CatalogItem } from '../core/api/catalog-db.api';
import { CatalogPicker } from '../shared/catalog-picker';
import { catchError, of } from 'rxjs';
import { FloweringType, GrowMedium, GrowSetup, SeedSex, Visibility } from '../core/api/api.models';
import { AccountApi } from '../core/api/account.api';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { FLOWERING_LABEL, MEDIUM_LABEL, SEED_SEX_LABEL, options, today } from '../shared/labels';

@Component({
  selector: 'app-new-diary-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, CatalogPicker],
  template: `
    <section class="mx-auto max-w-xl">
      <a routerLink="/diarios" class="text-sm text-fg-muted">← Diários</a>
      <h1 class="mt-1 text-2xl font-bold">Iniciar diário</h1>

      <form class="wd-card mt-4 grid gap-4" [formGroup]="form" (ngSubmit)="submit()">
        <div>
          <label class="wd-label" for="title">Nome do cultivo</label>
          <input id="title" class="wd-input" formControlName="title" maxlength="100" placeholder="Ex.: Tenda 80x80 – ciclo 3" />
        </div>
        <app-catalog-picker
          kind="breeders"
          label="Banco / breeder (opcional)"
          placeholder="Ex.: Barneys Farm"
          hint="Escolha o banco primeiro para ver só as strains dele."
          [(value)]="breeder"
        />
        <app-catalog-picker
          kind="strains"
          label="Strain"
          placeholder="Ex.: Bubba Kush"
          hint="Busca no catálogo da comunidade. Se não achar, é só adicionar."
          [breederId]="breeder()?.id"
          [(value)]="strain"
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="wd-label" for="flowering">Floração</label>
            <select id="flowering" class="wd-input" formControlName="floweringType">
              @for (o of floweringOptions; track o.value) {
                <option [value]="o.value">{{ o.label }}</option>
              }
            </select>
          </div>
          <div>
            <label class="wd-label" for="seed-sex">Semente</label>
            <select id="seed-sex" class="wd-input" formControlName="seedSex">
              @for (o of seedSexOptions; track o.value) {
                <option [value]="o.value">{{ o.label }}</option>
              }
            </select>
          </div>
        </div>

        <div>
          <label class="wd-label" for="setup">Setup</label>
          <select id="setup" class="wd-input" formControlName="setupId">
            <option value="">Nenhum (informar o meio abaixo)</option>
            @for (setup of setups(); track setup.id) {
              <option [value]="setup.id">{{ setup.name }}</option>
            }
          </select>
          <p class="mt-1 text-xs text-fg-muted">
            O setup é copiado para o diário com um toque. <a routerLink="/equipamentos" class="text-accent">Gerenciar setups</a>
          </p>
        </div>

        @if (!form.controls.setupId.value) {
          <div>
            <label class="wd-label" for="medium">Meio de cultivo</label>
            <select id="medium" class="wd-input" formControlName="medium">
              @for (o of mediumOptions; track o.value) {
                <option [value]="o.value">{{ o.label }}</option>
              }
            </select>
          </div>
        }

        <div class="grid gap-4 sm:grid-cols-2">
          <div>
            <label class="wd-label" for="started-on">Início do cultivo</label>
            <input id="started-on" type="date" class="wd-input" formControlName="startedOn" [max]="todayIso" />
          </div>
          <fieldset>
            <legend class="wd-label">Visibilidade</legend>
            <div class="grid grid-cols-2 gap-2">
              <label class="flex min-h-12 items-center gap-2 rounded-xl border border-line px-3">
                <input type="radio" value="PUBLIC" formControlName="visibility" class="size-5" /> Público
              </label>
              <label class="flex min-h-12 items-center gap-2 rounded-xl border border-line px-3">
                <input type="radio" value="PRIVATE" formControlName="visibility" class="size-5" /> Privado
              </label>
            </div>
          </fieldset>
        </div>

        @if (error()) {
          <p role="alert" class="rounded-xl bg-danger/10 px-3 py-2 text-sm text-danger">{{ error() }}</p>
        }
        <button type="submit" class="wd-button" [disabled]="form.invalid || !strain() || busy()">Criar diário</button>
      </form>
    </section>
  `,
})
export class NewDiaryPage {
  private readonly api = inject(DiariesApi);
  private readonly router = inject(Router);

  protected readonly todayIso = today();
  protected readonly floweringOptions = options(FLOWERING_LABEL);
  protected readonly seedSexOptions = options(SEED_SEX_LABEL);
  protected readonly mediumOptions = options(MEDIUM_LABEL);
  protected readonly setups = toSignal(inject(AccountApi).setups().pipe(catchError(() => of<GrowSetup[]>([]))), {
    initialValue: [],
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(100)]],
    floweringType: ['PHOTOPERIOD' as FloweringType],
    seedSex: ['FEMINIZED' as SeedSex],
    setupId: [''],
    medium: ['LIVING_SOIL' as GrowMedium],
    startedOn: [today()],
    visibility: ['PUBLIC' as Visibility],
  });
  protected readonly breeder = signal<CatalogItem | null>(null);
  protected readonly strain = signal<CatalogItem | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api
      .create({
        ...value,
        strainId: this.strain()!.id,
        setupId: value.setupId || undefined,
        // Com setup, o meio vem dele (a menos que o grower mude depois no setup).
        medium: value.setupId ? undefined : value.medium,
      })
      .subscribe({
        next: (diary) => this.router.navigate(['/diarios', diary.id]),
        error: (error) => {
          this.error.set(problemMessage(error));
          this.busy.set(false);
        },
      });
  }
}
