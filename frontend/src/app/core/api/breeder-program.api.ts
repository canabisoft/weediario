import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';
import { CatalogItem, StrainTraits, TestingStrainItem } from './catalog-db.api';
import { compact } from './diaries.api';

export type RepresentationStatus = 'PENDING' | 'APPROVED';

/** Pedido de representação da própria pessoa. */
export interface Representation {
  readonly breederId: string;
  readonly breeder: string;
  readonly status: RepresentationStatus;
  readonly requestedAt: string;
  readonly decidedAt?: string;
}

export interface BreederProgram {
  readonly representations: Representation[];
  /** Sementes dos breeders que a pessoa representa (em teste ou já no banco oficial). */
  readonly strains: TestingStrainItem[];
}

/** Só para a moderação: quem pediu e a nota de prova de vínculo. */
export interface RepresentativeRequest {
  readonly breederId: string;
  readonly breeder: string;
  readonly userId: string;
  readonly username: string;
  readonly status: RepresentationStatus;
  readonly note?: string;
  readonly requestedAt: string;
  readonly decidedAt?: string;
}

/**
 * Programa de breeders: representar um breeder, cadastrar sementes para teste e a fila da moderação.
 */
@Injectable({ providedIn: 'root' })
export class BreederProgramApi {
  private readonly http = inject(HttpClient);

  mine(): Observable<BreederProgram> {
    return this.http.get<BreederProgram>('/api/v1/breeder-program');
  }

  requestRepresentation(breederId: string, note: string): Observable<void> {
    return this.http.post<void>('/api/v1/breeder-program/representations', { breederId, note });
  }

  withdraw(breederId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/breeder-program/representations/${breederId}`);
  }

  registerStrain(breederId: string, name: string, traits: StrainTraits): Observable<CatalogItem> {
    return this.http.post<CatalogItem>('/api/v1/breeder-program/strains', { breederId, name, traits: compact(traits) });
  }

  requests(page = 0): Observable<Page<RepresentativeRequest>> {
    return this.http.get<Page<RepresentativeRequest>>('/api/v1/moderation/breeder-representatives', {
      params: { page, size: 20 },
    });
  }

  approve(breederId: string, userId: string): Observable<void> {
    return this.http.post<void>(`/api/v1/moderation/breeder-representatives/${breederId}/${userId}/approve`, {});
  }

  /** Recusa o pedido ou revoga o representante. */
  reject(breederId: string, userId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/moderation/breeder-representatives/${breederId}/${userId}`);
  }
}
