import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, SeedbankReview, SeedbankSummary, StrainReview, StrainSummary } from './api.models';
import { compact } from './diaries.api';

export interface NewReview {
  readonly vigor: number;
  readonly pestResistance: number;
  readonly easeOfGrowth: number;
  readonly yield: number;
  readonly terpeneProfile: number;
  readonly seedbankGermination?: number;
  readonly seedbankStability?: number;
  readonly seedbankService?: number;
  readonly report: string;
}

/** Catálogo de strains e bancos de sementes, e envio de reviews. */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  strains(query: string, page = 0): Observable<Page<StrainSummary>> {
    return this.http.get<Page<StrainSummary>>('/api/v1/strains', { params: searchParams(query, page) });
  }

  strain(id: string): Observable<StrainSummary> {
    return this.http.get<StrainSummary>(`/api/v1/strains/${id}`);
  }

  strainReviews(id: string, page = 0): Observable<Page<StrainReview>> {
    return this.http.get<Page<StrainReview>>(`/api/v1/strains/${id}/reviews`, { params: { page, size: 20 } });
  }

  seedbanks(query: string, page = 0): Observable<Page<SeedbankSummary>> {
    return this.http.get<Page<SeedbankSummary>>('/api/v1/seedbanks', { params: searchParams(query, page) });
  }

  seedbank(id: string): Observable<SeedbankSummary> {
    return this.http.get<SeedbankSummary>(`/api/v1/seedbanks/${id}`);
  }

  seedbankReviews(id: string, page = 0): Observable<Page<SeedbankReview>> {
    return this.http.get<Page<SeedbankReview>>(`/api/v1/seedbanks/${id}/reviews`, { params: { page, size: 20 } });
  }

  submitReview(diaryId: string, review: NewReview): Observable<{ id: string; strainId: string }> {
    return this.http.post<{ id: string; strainId: string }>(`/api/v1/diaries/${diaryId}/review`, compact(review));
  }
}

function searchParams(query: string, page: number): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size: 20 };
  if (query.trim()) {
    params['query'] = query.trim();
  }
  return params;
}
