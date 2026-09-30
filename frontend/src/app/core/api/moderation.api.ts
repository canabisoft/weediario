import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';
import { CatalogItem, CatalogKind, ProductCategory, RankingKind } from './catalog-db.api';

/** Comentário denunciado. Quem denunciou nunca é exposto, nem para a moderação. */
export interface ReportedComment {
  readonly id: string;
  readonly diaryId: string;
  readonly diaryTitle: string;
  readonly author: string;
  readonly body: string;
  readonly reports: number;
  readonly hidden: boolean;
  readonly createdAt: string;
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

  reportedComments(page = 0): Observable<Page<ReportedComment>> {
    return this.http.get<Page<ReportedComment>>('/api/v1/moderation/comments', { params: { page, size: 20 } });
  }

  commentCounts(): Observable<{ reported: number; hidden: number }> {
    return this.http.get<{ reported: number; hidden: number }>('/api/v1/moderation/comments/counts');
  }

  /** Denúncias improcedentes: o comentário volta a aparecer. */
  keepComment(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/moderation/comments/${id}/keep`, {});
  }

  removeComment(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/moderation/comments/${id}`);
  }

  pendingCatalog(kind: CatalogKind, page = 0): Observable<Page<PendingCatalogItem>> {
    return this.http.get<Page<PendingCatalogItem>>(`/api/v1/moderation/catalog/${kind}`, { params: { page, size: 20 } });
  }

  catalogCounts(): Observable<Record<RankingKind, number>> {
    return this.http.get<Record<RankingKind, number>>('/api/v1/moderation/catalog/counts');
  }
}
