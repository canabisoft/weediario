import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { PatientSummary, Today, TreatmentApi } from '../core/api/treatment.api';
import { Icon } from '../shared/icon';
import { HistoryPanel } from './history-panel';
import { MedicalNotice } from './medical-notice';
import { MedicinesPanel } from './medicines-panel';
import { SettingsPanel } from './settings-panel';
import { TodayPanel } from './today-panel';

type Tab = 'hoje' | 'remedios' | 'historico' | 'ajustes';

const TABS: readonly { id: Tab; label: string }[] = [
  { id: 'hoje', label: 'Hoje' },
  { id: 'remedios', label: 'Remédios' },
  { id: 'historico', label: 'Histórico' },
  { id: 'ajustes', label: 'Ajustes e relatório' },
];

type Loaded = { patient: PatientSummary; today: Today; error: null } | { patient: null; today: null; error: string };

/** Acompanhamento de um perfil: hoje, remédios, histórico e ajustes (sintomas, cuidadores, relatório). */
@Component({
  selector: 'app-treatment-patient-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, MedicalNotice, TodayPanel, MedicinesPanel, HistoryPanel, SettingsPanel],
  template: `
    <a routerLink="/tratamento" class="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-accent">
      <app-icon name="chevronLeft" class="size-4" /> Perfis
    </a>

    @if (loaded(); as state) {
      @if (state.patient; as patient) {
        <h1 class="mt-2 text-2xl font-bold">{{ patient.name }}</h1>
        @if (patient.role === 'CAREGIVER') {
          <p class="text-sm text-fg-muted">Você cuida deste perfil · responsável: {{ patient.owner }}</p>
        }
        <app-medical-notice class="mt-3 block" />

        <div class="mt-4 flex flex-wrap gap-2" role="tablist" aria-label="Seções do acompanhamento">
          @for (option of tabs; track option.id) {
            <button type="button" role="tab" class="min-h-11 rounded-full px-4 text-sm font-semibold"
                    [class]="tab() === option.id ? 'bg-accent text-accent-fg' : 'bg-surface-raised text-fg-muted'"
                    [attr.aria-selected]="tab() === option.id" (click)="select(option.id)">
              {{ option.label }}
            </button>
          }
        </div>

        <section class="mt-4" role="tabpanel">
          @switch (tab()) {
            @case ('remedios') {
              <app-medicines-panel [patientId]="patient.id" (changed)="refresh()" />
            }
            @case ('historico') {
              <app-history-panel [patientId]="patient.id" />
            }
            @case ('ajustes') {
              <app-settings-panel [patient]="patient" (changed)="refresh()" />
            }
            @default {
              <app-today-panel [patientId]="patient.id" [today]="state.today" (changed)="refresh()" />
            }
          }
        </section>
      } @else {
        <p class="wd-card mt-4 text-center">{{ state.error }}</p>
      }
    } @else {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class TreatmentPatientPage {
  private readonly api = inject(TreatmentApi);
  private readonly router = inject(Router);

  /** Parâmetros de rota e de consulta. */
  readonly patientId = input.required<string>();
  readonly aba = input<string | undefined>();

  protected readonly tabs = TABS;
  protected readonly tab = computed<Tab>(() => TABS.find((t) => t.id === this.aba())?.id ?? 'hoje');
  private readonly reload = signal(0);

  protected readonly loaded = toSignal(
    combineLatest([toObservable(this.patientId), toObservable(this.reload)]).pipe(
      switchMap(([id]) =>
        combineLatest([this.api.patients(), this.api.today(id)]).pipe(
          map(([overview, today]): Loaded => {
            const patient = overview.patients.find((p) => p.id === id);
            return patient
              ? { patient, today, error: null }
              : { patient: null, today: null, error: 'Perfil não encontrado.' };
          }),
          catchError((error) => of<Loaded>({ patient: null, today: null, error: problemMessage(error, 'Perfil não encontrado.') })),
        ),
      ),
    ),
  );

  protected select(tab: Tab): void {
    this.router.navigate([], { queryParams: { aba: tab === 'hoje' ? null : tab }, replaceUrl: true });
  }

  protected refresh(): void {
    this.reload.update((n) => n + 1);
  }
}
