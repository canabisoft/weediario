import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Observable, catchError, combineLatest, of, switchMap } from 'rxjs';
import { problemMessage } from '../core/api/problem';
import { Caregiver, PatientSummary, Symptom, SymptomKind, TreatmentApi } from '../core/api/treatment.api';
import { Icon } from '../shared/icon';
import { isoDay, saveFile } from './treatment-text';

/**
 * Ajustes do perfil: sintomas acompanhados, relatório para a consulta, cuidadores e o próprio perfil.
 * Só a conta responsável edita o perfil e os cuidadores; quem cuida pode registrar, acompanhar e gerar o relatório.
 */
@Component({
  selector: 'app-settings-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @let p = patient();
    <section class="wd-card grid gap-3" aria-labelledby="report-title">
      <h2 id="report-title" class="font-semibold">Relatório para a consulta (PDF)</h2>
      <p class="text-sm text-fg-muted">
        Junta os remédios usados, os mg por dia, a evolução dos sintomas e os efeitos anotados no período. É um registro
        para mostrar ao profissional de saúde, sem nenhuma recomendação.
      </p>
      <div class="flex flex-wrap items-end gap-2">
        <div>
          <label class="wd-label" for="report-from">De</label>
          <input id="report-from" type="date" class="wd-input text-base" [value]="from()" (input)="from.set($any($event.target).value)" />
        </div>
        <div>
          <label class="wd-label" for="report-to">Até</label>
          <input id="report-to" type="date" class="wd-input text-base" [value]="to()" (input)="to.set($any($event.target).value)" />
        </div>
        <button type="button" class="wd-button" [disabled]="busy()" (click)="report()">Gerar PDF</button>
      </div>
    </section>

    <section class="wd-card mt-4 grid gap-3" aria-labelledby="symptoms-title">
      <h2 id="symptoms-title" class="font-semibold">Sintomas acompanhados</h2>
      <p class="text-sm text-fg-muted">Escala de 0 a 10 (dor, ansiedade, sono...) ou contagem (crises, despertares).</p>
      <ul class="grid gap-2">
        @for (symptom of symptoms() ?? []; track symptom.id) {
          <li class="flex items-center gap-2" [class.opacity-60]="symptom.archived">
            <span class="flex-1">{{ symptom.name }} <span class="text-xs text-fg-muted">{{ symptom.kind === 'SCALE' ? '0–10' : 'contagem' }}{{ symptom.archived ? ' · arquivado' : '' }}</span></span>
            <button type="button" class="text-sm text-fg-muted" (click)="archiveSymptom(symptom)">{{ symptom.archived ? 'Reativar' : 'Arquivar' }}</button>
          </li>
        } @empty {
          <li class="text-sm text-fg-muted">Nenhum sintoma ainda.</li>
        }
      </ul>
      <form class="grid gap-2 sm:grid-cols-[1fr_auto_auto] sm:items-end" (submit)="$event.preventDefault(); addSymptom()">
        <div>
          <label class="wd-label" for="symptom-name">Novo sintoma</label>
          <input id="symptom-name" class="wd-input" maxlength="60" placeholder="Ex.: Dor lombar, Ansiedade, Crises"
                 [value]="symptomName()" (input)="symptomName.set($any($event.target).value)" />
        </div>
        <select class="wd-input text-base" aria-label="Tipo" [value]="symptomKind()" (change)="symptomKind.set($any($event.target).value)">
          <option value="SCALE">Escala 0–10</option>
          <option value="COUNT">Contagem</option>
        </select>
        <button type="submit" class="wd-button" [disabled]="!symptomName().trim() || busy()"><app-icon name="plus" class="size-5" /> Adicionar</button>
      </form>
    </section>

    <section class="wd-card mt-4 grid gap-3" aria-labelledby="caregivers-title">
      <h2 id="caregivers-title" class="font-semibold">Cuidadores</h2>
      @if (p.role === 'OWNER') {
        <p class="text-sm text-fg-muted">
          Quem você convidar pelo apelido pode registrar usos e notas e gerar o relatório deste perfil, depois de aceitar o
          convite. Só você edita o perfil e os cuidadores.
        </p>
        <ul class="grid gap-2">
          @for (caregiver of caregivers() ?? []; track caregiver.username) {
            <li class="flex items-center gap-2">
              <span class="flex-1">{{ caregiver.username }} <span class="text-xs text-fg-muted">{{ caregiver.accepted ? 'cuida deste perfil' : 'convite pendente' }}</span></span>
              <button type="button" class="text-sm text-danger" (click)="removeCaregiver(caregiver)">Remover</button>
            </li>
          } @empty {
            <li class="text-sm text-fg-muted">Ninguém além de você.</li>
          }
        </ul>
        <form class="grid gap-2 sm:grid-cols-[1fr_auto] sm:items-end" (submit)="$event.preventDefault(); invite()">
          <div>
            <label class="wd-label" for="caregiver-username">Convidar pelo apelido</label>
            <input id="caregiver-username" class="wd-input" maxlength="30" autocomplete="off" [value]="username()"
                   (input)="username.set($any($event.target).value)" />
          </div>
          <button type="submit" class="wd-button" [disabled]="!username().trim() || busy()">Convidar</button>
        </form>
      } @else {
        <p class="text-sm text-fg-muted">Você cuida deste perfil a convite de <strong>{{ p.owner }}</strong>.</p>
        <button type="button" class="wd-button w-fit bg-surface-raised text-danger" (click)="leave()">Deixar de cuidar deste perfil</button>
      }
    </section>

    @if (p.role === 'OWNER') {
      <section class="wd-card mt-4 grid gap-3" aria-labelledby="profile-title">
        <h2 id="profile-title" class="font-semibold">Perfil</h2>
        <form class="grid gap-2" (submit)="$event.preventDefault(); rename()">
          <label class="wd-label" for="profile-name">Apelido do perfil</label>
          <input id="profile-name" class="wd-input" maxlength="60" [value]="name()" (input)="name.set($any($event.target).value)" />
          <label class="wd-label" for="profile-notes">Observações (diagnóstico, profissional que acompanha...)</label>
          <textarea id="profile-notes" rows="3" maxlength="1000" class="wd-input py-2 text-base" [value]="notes()" (input)="notes.set($any($event.target).value)"></textarea>
          <button type="submit" class="wd-button w-fit" [disabled]="!name().trim() || busy()">Salvar</button>
        </form>
        @if (confirmDelete()) {
          <div class="flex flex-wrap gap-2">
            <button type="button" class="wd-button bg-danger text-white" (click)="deletePatient()">Apagar perfil e todo o histórico</button>
            <button type="button" class="wd-button bg-surface-raised text-fg" (click)="confirmDelete.set(false)">Cancelar</button>
          </div>
        } @else {
          <button type="button" class="wd-button w-fit bg-surface-raised text-danger" (click)="confirmDelete.set(true)">Apagar este perfil</button>
        }
      </section>
    }

    @if (message()) {
      <p role="status" class="mt-3 text-sm">{{ message() }}</p>
    }
    @if (error()) {
      <p role="alert" class="mt-3 text-sm text-danger">{{ error() }}</p>
    }
  `,
})
export class SettingsPanel {
  private readonly api = inject(TreatmentApi);
  private readonly router = inject(Router);

  readonly patient = input.required<PatientSummary>();
  readonly changed = output<void>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  private readonly reload = signal(0);

  protected readonly from = signal(isoDay(new Date(Date.now() - 29 * 86_400_000)));
  protected readonly to = signal(isoDay(new Date()));
  protected readonly symptomName = signal('');
  protected readonly symptomKind = signal<SymptomKind>('SCALE');
  protected readonly username = signal('');
  protected readonly name = signal('');
  protected readonly notes = signal('');

  protected readonly symptoms = toSignal(
    combineLatest([toObservable(this.patient), toObservable(this.reload)]).pipe(
      switchMap(([p]) => this.api.symptoms(p.id, true).pipe(catchError(() => of<Symptom[]>([])))),
    ),
  );

  protected readonly caregivers = toSignal(
    combineLatest([toObservable(this.patient), toObservable(this.reload)]).pipe(
      switchMap(([p]) => (p.role === 'OWNER' ? this.api.caregivers(p.id) : of<Caregiver[]>([])).pipe(catchError(() => of<Caregiver[]>([])))),
    ),
  );

  constructor() {
    toObservable(this.patient).subscribe((p) => {
      this.name.set(p.name);
      this.notes.set(p.notes ?? '');
    });
  }

  protected report(): void {
    this.run(this.api.report(this.patient().id, this.from(), this.to()), (blob) => {
      saveFile(blob as Blob, `relatorio-tratamento-${this.from()}-a-${this.to()}.pdf`);
      this.message.set('Relatório gerado.');
    });
  }

  protected addSymptom(): void {
    this.run(this.api.addSymptom(this.patient().id, this.symptomName().trim(), this.symptomKind()), () => {
      this.symptomName.set('');
      this.changed.emit();
    });
  }

  protected archiveSymptom(symptom: Symptom): void {
    this.run(this.api.archiveSymptom(this.patient().id, symptom.id, !symptom.archived), () => this.changed.emit());
  }

  protected invite(): void {
    this.run(this.api.invite(this.patient().id, this.username().trim()), () => {
      this.message.set(`Convite enviado para ${this.username().trim()}. A pessoa aceita na área de Tratamento.`);
      this.username.set('');
    });
  }

  protected removeCaregiver(caregiver: Caregiver): void {
    this.run(this.api.removeCaregiver(this.patient().id, caregiver.username));
  }

  protected leave(): void {
    this.run(this.api.leave(this.patient().id), () => this.router.navigate(['/tratamento']));
  }

  protected rename(): void {
    this.run(this.api.updatePatient(this.patient().id, this.name().trim(), this.notes().trim() || undefined), () => {
      this.message.set('Perfil salvo.');
      this.changed.emit();
    });
  }

  protected deletePatient(): void {
    this.run(this.api.deletePatient(this.patient().id), () => this.router.navigate(['/tratamento']));
  }

  private run<T>(request: Observable<T>, done?: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    request.subscribe({
      next: (value) => {
        this.busy.set(false);
        done?.(value);
        this.reload.update((n) => n + 1);
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(problemMessage(error));
      },
    });
  }
}
