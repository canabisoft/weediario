import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';
import { CatalogItem, CatalogKind, ProductCategory, RankingKind } from './catalog-db.api';

export type ContentType = 'COMMENT' | 'DIARY' | 'REVIEW' | 'RECIPE' | 'RECIPE_COMMENT' | 'PROFILE';
export type ReportReason = 'ILLEGAL_COMMERCE' | 'TRAFFICKING_APOLOGY' | 'INAPPROPRIATE' | 'SPAM' | 'OTHER';
export type ModerationReason = ReportReason | 'UNFOUNDED';
export type Verdict = 'KEEP' | 'REMOVE';
export type AccountAction = 'NONE' | 'SUSPEND' | 'BAN';

/**
 * Item da fila da moderação. Nunca traz quem denunciou: só quantas denúncias e por qual motivo.
 * {@code held}: fora do público aguardando decisão (por denúncias ou pelo filtro anti-comércio).
 */
export interface QueueItem {
  readonly type: ContentType;
  readonly id: string;
  readonly exists: boolean;
  readonly author?: string;
  readonly authorStatus?: string;
  readonly title?: string;
  readonly text?: string;
  readonly linkId?: string;
  readonly held: boolean;
  readonly cause?: 'REPORTS' | 'AUTO_FILTER';
  readonly detail?: string;
  readonly reports: number;
  readonly reasons: Partial<Record<ReportReason, number>>;
  readonly since: string;
}

export interface QueueCounts {
  readonly pending: number;
  readonly held: number;
  readonly byType: Partial<Record<ContentType, number>>;
}

/** Cadastro da comunidade ainda não verificado, com os itens parecidos que podem ser a mesma coisa. */
export interface PendingCatalogItem {
  readonly id: string;
  readonly name: string;
  readonly parentId?: string;
  readonly parentName?: string;
  readonly category?: ProductCategory;
  readonly createdAt: string;
  readonly usage: number;
  readonly similar: CatalogItem[];
}

@Injectable({ providedIn: 'root' })
export class ModerationApi {
  private readonly http = inject(HttpClient);

  /** Denúncia pela comunidade (qualquer conteúdo público). */
  report(targetType: ContentType, targetId: string, reason: ReportReason): Observable<{ hidden: boolean }> {
    if (targetType === 'PROFILE') {
      // Perfil vai pelo apelido: a API pública não expõe o id interno das contas.
      return this.http.post<{ hidden: boolean }>(`/api/v1/reports/profiles/${encodeURIComponent(targetId)}`, { reason });
    }
    return this.http.post<{ hidden: boolean }>('/api/v1/reports', { targetType, targetId, reason });
  }

  queue(page = 0): Observable<Page<QueueItem>> {
    return this.http.get<Page<QueueItem>>('/api/v1/moderation/queue', { params: { page, size: 20 } });
  }

  queueCounts(): Observable<QueueCounts> {
    return this.http.get<QueueCounts>('/api/v1/moderation/queue/counts');
  }

  decide(decision: {
    targetType: ContentType;
    targetId: string;
    verdict: Verdict;
    account: AccountAction;
    reason?: ModerationReason;
    note?: string;
  }): Observable<void> {
    return this.http.post<void>('/api/v1/moderation/decisions', decision);
  }

  reinstate(username: string, note?: string): Observable<void> {
    return this.http.post<void>(`/api/v1/moderation/users/${encodeURIComponent(username)}/reinstate`, { note });
  }

  /** Log de moderação em CSV (só admin). */
  log(username?: string, from?: string, to?: string): Observable<Blob> {
    const params: Record<string, string> = {};
    if (username) params['username'] = username;
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.http.get('/api/v1/moderation/log.csv', { params, responseType: 'blob' });
  }

  pendingCatalog(kind: CatalogKind, page = 0): Observable<Page<PendingCatalogItem>> {
    return this.http.get<Page<PendingCatalogItem>>(`/api/v1/moderation/catalog/${kind}`, { params: { page, size: 20 } });
  }

  catalogCounts(): Observable<Record<RankingKind, number>> {
    return this.http.get<Record<RankingKind, number>>('/api/v1/moderation/catalog/counts');
  }
}
