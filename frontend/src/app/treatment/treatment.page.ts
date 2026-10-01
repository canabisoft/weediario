import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { ConsentStatus, Invite, PatientSummary, TreatmentApi } from '../core/api/treatment.api';
import { Icon } from '../shared/icon';
import { MedicalNotice } from './medical-notice';
import { TreatmentConsent } from './treatment-consent';
import { saveFile } from './treatment-text';

/**
 * Entrada do acompanhamento de tratamento: termo de consentimento, perfis (próprio ou de dependentes),
 * convites para cuidar e os controles de exportar e apagar tudo.
 */
@Component({
  selector: 'app-treatment-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, Icon, MedicalNotice, TreatmentConsent],
  template: `
    <h1 class="text-2xl font-bold">Tratamento</h1>
    <p class="text-fg-muted">Diário de uso de cannabis medicinal: remédios, usos, sintomas e relatório para a consulta.</p>
    <app-medical-notice class="mt-3 block" />

    @if (consent(); as status) {
      @if (!status.accepted) {
        <app-treatment-consent class="mt-4 block" (accepted)="onConsent($event)" />
      } @else {
        @if (invites().length) {
          <section class="mt-6" aria-labelledby="invites-title">
            <h2 id="invites-title" class="text-lg font-semibold">Convites para cuidar</h2>
            <ul class="mt-3 grid gap-2">
              @for (invite of invites(); track invite.patientId) {
                <li class="wd-card flex flex-wrap items-center gap-3">
                  <span class="min-w-0 flex-1">
                    <strong>{{ invite.owner }}</strong> convidou você para registrar o tratamento de
                    <strong>{{ invite.patientName }}</strong>.
                  </span>
                  <button type="button" class="wd-button" (click)="accept(invite)">Aceitar</button>
                  <button type="button" class="wd-button bg-surface-raised text-fg" (click)="decline(invite)">Recusar</button>
                </li>
              }
            </ul>
          </section>
        }

        <section class="mt-6" aria-labelledby="patients-title">
          <h2 id="patients-title" class="text-lg font-semibold">Perfis</h2>
          <ul class="mt-3 grid gap-3 sm:grid-cols-2">
            @for (patient of patients(); track patient.id) {
              <li>
                <a [routerLink]="['/tratamento', patient.id]" class="wd-card flex items-center gap-3 hover:border-accent/50">
                  <span class="grid size-11 shrink-0 place-items-center rounded-full bg-accent/15 text-accent">
                    <app-icon name="user" class="size-5" />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="block truncate font-semibold">{{ patient.name }}</span>
                    <span class="block text-xs text-fg-muted">
                      @if (patient.role === 'OWNER') {
                        Você é responsável{{ patient.caregivers ? ' · ' + patient.caregivers + ' cuidador(es)' : '' }}
                      } @else {
                        Você cuida · responsável: {{ patient.owner }}
                      }
                    </span>
                  </span>
                  <app-icon name="chevronRight" class="size-5 text-fg-muted" />
                </a>
              </li>
            } @empty {
              <li class="wd-card text-fg-muted sm:col-span-2">
                Crie um perfil para você ou para quem você acompanha (filho, familiar). Use só um apelido.
              </li>
            }
          </ul>

          <form class="wd-card mt-3 grid gap-2 sm:grid-cols-[1fr_auto] sm:items-end" (submit)="$event.preventDefault(); create()">
            <div>
              <label class="wd-label" for="patient-name">Novo perfil</label>
              <input id="patient-name" class="wd-input" name="name" maxlength="60" placeholder="Ex.: Eu, Mãe, Joãozinho"
                     [(ngModel)]="newName" />
            </div>
            <button type="submit" class="wd-button" [disabled]="!newName.trim() || busy()">
              <app-icon name="plus" class="size-5" /> Criar perfil
            </button>
          </form>
        </section>

        <section class="mt-8 wd-card grid gap-3" aria-labelledby="data-title">
          <h2 id="data-title" class="font-semibold">Seus dados de saúde</h2>
          <p class="text-sm text-fg-muted">
            Termo aceito na versão {{ status.acceptedVersion }}. Você pode baixar tudo o que registrou nos perfis de que
            é responsável, ou apagar tudo (perfis, remédios, usos, notas e acessos de cuidador). Apagar é definitivo.
          </p>
          <div class="flex flex-wrap gap-2">
            <button type="button" class="wd-button bg-surface-raised text-fg" [disabled]="busy()" (click)="exportAll()">Baixar meus dados (JSON)</button>
            @if (confirmErase()) {
              <button type="button" class="wd-button bg-danger text-white" [disabled]="busy()" (click)="eraseAll()">Sim, apagar tudo agora</button>
              <button type="button" class="wd-button bg-surface-raised text-fg" (click)="confirmErase.set(false)">Cancelar</button>
            } @else {
              <button type="button" class="wd-button bg-surface-raised text-danger" (click)="confirmErase.set(true)">Apagar tudo e revogar o consentimento</button>
            }
          </div>
        </section>
      }
    } @else if (!error()) {
      <div class="mt-4 h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
    @if (error()) {
      <p role="alert" class="mt-3 text-sm text-danger">{{ error() }}</p>
    }
  `,
})
export class TreatmentPage implements OnInit {
  private readonly api = inject(TreatmentApi);
  private readonly router = inject(Router);

  protected readonly consent = signal<ConsentStatus | null>(null);
  protected readonly patients = signal<PatientSummary[]>([]);
  protected readonly invites = signal<Invite[]>([]);
  protected readonly busy = signal(false);
  protected readonly confirmErase = signal(false);
  protected readonly error = signal<string | null>(null);
  protected newName = '';

  ngOnInit(): void {
    this.api.consent().subscribe({
      next: (status) => {
        this.consent.set(status);
        if (status.accepted) {
          this.load();
        }
      },
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  protected onConsent(status: ConsentStatus): void {
    this.consent.set(status);
    this.load();
  }

  protected create(): void {
    this.run(this.api.createPatient(this.newName.trim()), (created) => {
      this.newName = '';
      this.router.navigate(['/tratamento', created.id], { queryParams: { aba: 'ajustes' } });
    });
  }

  protected accept(invite: Invite): void {
    this.run(this.api.acceptInvite(invite.patientId), () => this.load());
  }

  protected decline(invite: Invite): void {
    this.run(this.api.leave(invite.patientId), () => this.load());
  }

  protected exportAll(): void {
    this.run(this.api.exportAll(), (blob) => saveFile(blob, 'weediario-tratamento.json'));
  }

  protected eraseAll(): void {
    this.run(this.api.eraseAll(), () => {
      this.confirmErase.set(false);
      this.patients.set([]);
      this.invites.set([]);
      this.consent.set({ accepted: false, currentVersion: this.consent()?.currentVersion ?? '' });
    });
  }

  private load(): void {
    this.api.patients().subscribe({
      next: ({ patients, invites }) => {
        this.patients.set(patients);
        this.invites.set(invites);
      },
      error: (error) => this.error.set(problemMessage(error)),
    });
  }

  private run<T>(request: Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        done(value);
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
