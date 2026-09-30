import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DiaryCard,
  DiaryStatus,
  DiaryUpdate,
  FeedSort,
  FloweringType,
  GrowDiaryView,
  GrowMedium,
  GrowthStage,
  Page,
  SeedSex,
  Visibility,
} from './api.models';

export interface FeedQuery {
  readonly stage?: GrowthStage;
  readonly medium?: GrowMedium;
  readonly status?: DiaryStatus;
  readonly strain?: string;
  readonly author?: string;
  readonly sort?: FeedSort;
  readonly following?: boolean;
  readonly page?: number;
  readonly size?: number;
}

export interface NewDiary {
  readonly title: string;
  /** Strain escolhida no catálogo (preferível) ou, na falta dela, o nome digitado. */
  readonly strainId?: string;
  readonly strainName?: string;
  readonly breeder?: string;
  readonly floweringType: FloweringType;
  readonly seedSex: SeedSex;
  readonly medium?: GrowMedium;
  readonly visibility: Visibility;
  readonly startedOn?: string;
  readonly setupId?: string;
}

export interface NewUpdate {
  readonly stage: GrowthStage;
  readonly recordedOn?: string;
  readonly note?: string;
  readonly temperatureCelsius?: number;
  readonly relativeHumidity?: number;
  readonly wateringPh?: number;
  readonly plantHeightCm?: number;
  readonly photoIds: string[];
}

@Injectable({ providedIn: 'root' })
export class DiariesApi {
  private readonly http = inject(HttpClient);

  feed(query: FeedQuery): Observable<Page<DiaryCard>> {
    return this.http.get<Page<DiaryCard>>('/api/v1/feed/diaries', { params: toParams(query) });
  }

  mine(page = 0): Observable<Page<DiaryCard>> {
    return this.http.get<Page<DiaryCard>>('/api/v1/me/diaries', { params: { page, size: 20 } });
  }

  get(id: string): Observable<DiaryCard> {
    return this.http.get<DiaryCard>(`/api/v1/diaries/${id}`);
  }

  create(diary: NewDiary): Observable<GrowDiaryView> {
    return this.http.post<GrowDiaryView>('/api/v1/diaries', compact(diary));
  }

  complete(id: string, harvestedOn?: string, dryGrams?: number, plants?: number): Observable<GrowDiaryView> {
    return this.http.post<GrowDiaryView>(`/api/v1/diaries/${id}/complete`, compact({ harvestedOn, dryGrams, plants }));
  }

  cancel(id: string): Observable<GrowDiaryView> {
    return this.http.post<GrowDiaryView>(`/api/v1/diaries/${id}/cancel`, {});
  }

  recordHarvest(id: string, dryGrams: number, plants: number): Observable<GrowDiaryView> {
    return this.http.put<GrowDiaryView>(`/api/v1/diaries/${id}/harvest`, { dryGrams, plants });
  }

  changeVisibility(id: string, visibility: Visibility): Observable<GrowDiaryView> {
    return this.http.put<GrowDiaryView>(`/api/v1/diaries/${id}/visibility`, { visibility });
  }

  updates(id: string, page = 0): Observable<Page<DiaryUpdate>> {
    return this.http.get<Page<DiaryUpdate>>(`/api/v1/diaries/${id}/updates`, { params: { page, size: 10 } });
  }

  publishUpdate(id: string, update: NewUpdate): Observable<DiaryUpdate> {
    return this.http.post<DiaryUpdate>(`/api/v1/diaries/${id}/updates`, compact(update));
  }

  deleteUpdate(diaryId: string, updateId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/diaries/${diaryId}/updates/${updateId}`);
  }

  /** Fotos de diários privados precisam do token, que uma tag img não envia: baixa como blob. */
  photoBlob(url: string): Observable<Blob> {
    return this.http.get(url, { responseType: 'blob' });
  }
}

/** Remove campos vazios para não enviar "" ou null ao backend (payload estrito). */
export function compact<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  ) as Partial<T>;
}

function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(compact(query))) {
    params = params.set(key, String(value));
  }
  return params;
}
