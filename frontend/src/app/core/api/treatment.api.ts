import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { compact } from './diaries.api';

export type MedicineForm = 'OIL' | 'TINCTURE' | 'FLOWER' | 'GUMMY' | 'CAPSULE' | 'TOPICAL' | 'EXTRACT';
export type DoseUnit = 'DROP' | 'ML' | 'G' | 'UNIT' | 'APPLICATION';
export type StockUnit = 'ML' | 'G' | 'UNIT';
export type ConcentrationUnit = 'MG_PER_ML' | 'PERCENT' | 'MG_PER_UNIT' | 'MG_PER_G';
export type Route = 'SUBLINGUAL' | 'ORAL' | 'INHALED' | 'TOPICAL' | 'OTHER';
export type Origin = 'HOME_GROWN' | 'ASSOCIATION' | 'PHARMACY' | 'IMPORTED' | 'OTHER';
export type SymptomKind = 'SCALE' | 'COUNT';
export type RatingMoment = 'BEFORE' | 'AFTER' | 'CHECKIN';
export type SideEffect =
  | 'DIZZINESS'
  | 'DROWSINESS'
  | 'DRY_MOUTH'
  | 'RED_EYES'
  | 'TACHYCARDIA'
  | 'ANXIETY'
  | 'HUNGER'
  | 'NAUSEA'
  | 'HEADACHE'
  | 'OTHER';
export type Intensity = 'MILD' | 'MODERATE' | 'SEVERE';
export type AccessRole = 'OWNER' | 'CAREGIVER';

export interface ConsentStatus {
  readonly accepted: boolean;
  readonly currentVersion: string;
  readonly acceptedVersion?: string;
  readonly acceptedAt?: string;
}

export interface PatientSummary {
  readonly id: string;
  readonly name: string;
  readonly notes?: string;
  readonly role: AccessRole;
  readonly owner: string;
  readonly caregivers: number;
}

export interface Invite {
  readonly patientId: string;
  readonly patientName: string;
  readonly owner: string;
  readonly invitedAt: string;
}

export interface Caregiver {
  readonly username: string;
  readonly accepted: boolean;
  readonly invitedAt: string;
}

export interface Symptom {
  readonly id: string;
  readonly name: string;
  readonly kind: SymptomKind;
  readonly archived: boolean;
}

/** Lote como aparece no painel; {@code remaining} é calculado (inicial − usos ± ajustes). */
export interface Medicine {
  readonly id: string;
  readonly name: string;
  readonly form: MedicineForm;
  readonly origin: Origin;
  readonly thc?: number;
  readonly cbd?: number;
  readonly concentrationUnit: ConcentrationUnit;
  readonly stockUnit: StockUnit;
  readonly doseUnits: DoseUnit[];
  readonly dropsPerMl?: number;
  readonly initialQuantity: number;
  readonly remaining: number;
  readonly estimatedDaysLeft?: number;
  readonly expired: boolean;
  readonly expiresSoon: boolean;
  readonly manufacturedOn?: string;
  readonly expiresOn?: string;
  readonly notes?: string;
  readonly archived: boolean;
}

export interface MedicineInput {
  readonly name: string;
  readonly form: MedicineForm;
  readonly origin?: Origin;
  readonly thc?: number;
  readonly cbd?: number;
  readonly dropsPerMl?: number;
  readonly initialQuantity: number;
  readonly manufacturedOn?: string;
  readonly expiresOn?: string;
  readonly notes?: string;
}

export interface Rating {
  readonly id: string;
  readonly doseId?: string;
  readonly symptomId: string;
  readonly symptom: string;
  readonly kind: SymptomKind;
  readonly moment: RatingMoment;
  readonly value: number;
  readonly recordedAt: string;
  readonly recordedBy?: string;
}

export interface SideEffectEntry {
  readonly id: string;
  readonly doseId?: string;
  readonly effect: SideEffect;
  readonly intensity: Intensity;
  readonly recordedAt: string;
}

export interface DoseEntry {
  readonly id: string;
  readonly batchId: string;
  readonly batchName: string;
  readonly form: MedicineForm;
  readonly amount: number;
  readonly unit: DoseUnit;
  readonly route: Route;
  readonly takenAt: string;
  readonly stockQuantity?: number;
  readonly thcMg?: number;
  readonly cbdMg?: number;
  readonly notes?: string;
  readonly recordedBy?: string;
  readonly ratings: Rating[];
  readonly sideEffects: SideEffectEntry[];
}

export interface PendingAfter {
  readonly doseId: string;
  readonly batchName: string;
  readonly takenAt: string;
  readonly symptoms: Symptom[];
}

export interface Today {
  readonly role: AccessRole;
  readonly symptoms: Symptom[];
  readonly medicines: Medicine[];
  readonly doses: DoseEntry[];
  readonly pendingAfter: PendingAfter[];
  readonly thcMgToday?: number;
  readonly cbdMgToday?: number;
}

export interface Timeline {
  readonly from: string;
  readonly to: string;
  readonly doses: DoseEntry[];
  readonly checkins: Rating[];
  readonly sideEffects: SideEffectEntry[];
}

export interface DayPoint {
  readonly day: string;
  readonly doses: number;
  readonly thcMg?: number;
  readonly cbdMg?: number;
  /** Média do dia (escala) ou total (contagem), por id de sintoma. */
  readonly symptoms: Record<string, number>;
}

export interface Series {
  readonly from: string;
  readonly to: string;
  readonly symptoms: Symptom[];
  readonly days: DayPoint[];
}

export interface RatingInput {
  readonly symptomId: string;
  readonly value: number;
}

export interface NewDose {
  readonly batchId: string;
  readonly amount: number;
  readonly unit: DoseUnit;
  readonly route?: Route;
  readonly takenAt?: string;
  readonly notes?: string;
  readonly before?: RatingInput[];
  readonly sideEffects?: { effect: SideEffect; intensity: Intensity }[];
}

const BASE = '/api/v1/me/treatment';

/**
 * Acompanhamento de tratamento: dados de saúde, só da própria conta e de cuidadores convidados.
 * Nada aqui é recomendação: a API só guarda e devolve o que foi registrado.
 */
@Injectable({ providedIn: 'root' })
export class TreatmentApi {
  private readonly http = inject(HttpClient);

  consent(): Observable<ConsentStatus> {
    return this.http.get<ConsentStatus>(`${BASE}/consent`);
  }

  acceptConsent(termsVersion: string): Observable<ConsentStatus> {
    return this.http.put<ConsentStatus>(`${BASE}/consent`, { termsVersion });
  }

  /** Revoga o consentimento e apaga tudo do módulo. */
  eraseAll(): Observable<void> {
    return this.http.delete<void>(BASE);
  }

  exportAll(): Observable<Blob> {
    return this.http.get(`${BASE}/export`, { responseType: 'blob' });
  }

  patients(): Observable<{ patients: PatientSummary[]; invites: Invite[] }> {
    return this.http.get<{ patients: PatientSummary[]; invites: Invite[] }>(`${BASE}/patients`);
  }

  createPatient(name: string, notes?: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${BASE}/patients`, compact({ name, notes }));
  }

  updatePatient(id: string, name: string, notes?: string): Observable<void> {
    return this.http.put<void>(`${BASE}/patients/${id}`, compact({ name, notes }));
  }

  deletePatient(id: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/patients/${id}`);
  }

  caregivers(patientId: string): Observable<Caregiver[]> {
    return this.http.get<Caregiver[]>(`${BASE}/patients/${patientId}/caregivers`);
  }

  invite(patientId: string, username: string): Observable<void> {
    return this.http.post<void>(`${BASE}/patients/${patientId}/caregivers`, { username });
  }

  removeCaregiver(patientId: string, username: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/patients/${patientId}/caregivers/${encodeURIComponent(username)}`);
  }

  acceptInvite(patientId: string): Observable<void> {
    return this.http.post<void>(`${BASE}/invites/${patientId}`, {});
  }

  /** Recusar convite ou deixar de cuidar. */
  leave(patientId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/invites/${patientId}`);
  }

  symptoms(patientId: string, archived = false): Observable<Symptom[]> {
    return this.http.get<Symptom[]>(`${BASE}/patients/${patientId}/symptoms`, { params: { archived } });
  }

  addSymptom(patientId: string, name: string, kind: SymptomKind): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${BASE}/patients/${patientId}/symptoms`, { name, kind });
  }

  archiveSymptom(patientId: string, symptomId: string, archived: boolean): Observable<void> {
    return this.http.patch<void>(`${BASE}/patients/${patientId}/symptoms/${symptomId}`, { archived });
  }

  medicines(patientId: string, archived = false): Observable<Medicine[]> {
    return this.http.get<Medicine[]>(`${BASE}/patients/${patientId}/medicines`, { params: { archived } });
  }

  registerMedicine(patientId: string, input: MedicineInput): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${BASE}/patients/${patientId}/medicines`, compact(input));
  }

  archiveMedicine(patientId: string, batchId: string, archived: boolean): Observable<void> {
    return this.http.patch<void>(`${BASE}/patients/${patientId}/medicines/${batchId}`, { archived });
  }

  adjustStock(patientId: string, batchId: string, kind: 'ADJUSTMENT' | 'DISCARD', quantity: number): Observable<void> {
    return this.http.post<void>(`${BASE}/patients/${patientId}/medicines/${batchId}/adjustments`, { kind, quantity });
  }

  today(patientId: string): Observable<Today> {
    return this.http.get<Today>(`${BASE}/patients/${patientId}/today`);
  }

  recordDose(patientId: string, dose: NewDose): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${BASE}/patients/${patientId}/doses`, compact(dose));
  }

  deleteDose(patientId: string, doseId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/patients/${patientId}/doses/${doseId}`);
  }

  rateAfter(patientId: string, doseId: string, ratings: RatingInput[]): Observable<void> {
    return this.http.put<void>(`${BASE}/patients/${patientId}/doses/${doseId}/after`, { ratings });
  }

  /** @param at para anotar um dia anterior; sem ele, vale agora */
  checkin(patientId: string, ratings: RatingInput[], at?: string): Observable<void> {
    return this.http.post<void>(`${BASE}/patients/${patientId}/checkins`, compact({ ratings, at }));
  }

  timeline(patientId: string, from?: string, to?: string): Observable<Timeline> {
    return this.http.get<Timeline>(`${BASE}/patients/${patientId}/timeline`, { params: compact({ from, to }) });
  }

  series(patientId: string, from?: string, to?: string): Observable<Series> {
    return this.http.get<Series>(`${BASE}/patients/${patientId}/series`, { params: compact({ from, to }) });
  }

  report(patientId: string, from?: string, to?: string): Observable<Blob> {
    return this.http.get(`${BASE}/patients/${patientId}/report`, {
      params: compact({ from, to }),
      responseType: 'blob',
    });
  }
}
