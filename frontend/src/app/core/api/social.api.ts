import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthoredReview,
  Comment,
  FollowedGrower,
  GrowerProfile,
  Page,
  PublicSetup,
  RankedGrowerDto,
  ReactionSummary,
  ReactionType,
} from './api.models';

export type RankingSortDto = 'DIARIES' | 'LIKES' | 'GRAMS_PER_PLANT' | 'REVIEWS';

@Injectable({ providedIn: 'root' })
export class SocialApi {
  private readonly http = inject(HttpClient);

  comments(diaryId: string, page = 0): Observable<Page<Comment>> {
    return this.http.get<Page<Comment>>(`/api/v1/diaries/${diaryId}/comments`, { params: { page, size: 30 } });
  }

  /** Com {@code parentId}, responde a um comentário (a resposta fica sempre no comentário raiz). */
  comment(diaryId: string, body: string, parentId?: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`/api/v1/diaries/${diaryId}/comments`, parentId ? { body, parentId } : { body });
  }

  /** "Ver mais": todas as respostas de um comentário raiz. */
  replies(diaryId: string, commentId: string): Observable<Page<Comment>> {
    return this.http.get<Page<Comment>>(`/api/v1/diaries/${diaryId}/comments/${commentId}/replies`, { params: { size: 50 } });
  }

  reactToComment(diaryId: string, commentId: string, type: ReactionType): Observable<ReactionSummary> {
    return this.http.put<ReactionSummary>(`/api/v1/diaries/${diaryId}/comments/${commentId}/reactions/${type}`, {});
  }

  unreactToComment(diaryId: string, commentId: string, type: ReactionType): Observable<ReactionSummary> {
    return this.http.delete<ReactionSummary>(`/api/v1/diaries/${diaryId}/comments/${commentId}/reactions/${type}`);
  }

  deleteComment(diaryId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/diaries/${diaryId}/comments/${commentId}`);
  }

  reactions(diaryId: string): Observable<ReactionSummary> {
    return this.http.get<ReactionSummary>(`/api/v1/diaries/${diaryId}/reactions`);
  }

  react(diaryId: string, type: ReactionType): Observable<ReactionSummary> {
    return this.http.put<ReactionSummary>(`/api/v1/diaries/${diaryId}/reactions/${type}`, {});
  }

  unreact(diaryId: string, type: ReactionType): Observable<ReactionSummary> {
    return this.http.delete<ReactionSummary>(`/api/v1/diaries/${diaryId}/reactions/${type}`);
  }

  ranking(sort: RankingSortDto, query: string): Observable<Page<RankedGrowerDto>> {
    const params: Record<string, string | number> = { sort, size: 50 };
    if (query.trim()) {
      params['q'] = query.trim();
    }
    return this.http.get<Page<RankedGrowerDto>>('/api/v1/growers/ranking', { params });
  }

  profile(username: string): Observable<GrowerProfile> {
    return this.http.get<GrowerProfile>(`/api/v1/growers/${encodeURIComponent(username)}`);
  }

  /** Setups já usados em diários públicos do grower. */
  publicSetups(username: string): Observable<PublicSetup[]> {
    return this.http.get<PublicSetup[]>(`/api/v1/growers/${encodeURIComponent(username)}/setups`);
  }

  reviewsBy(username: string, page = 0): Observable<Page<AuthoredReview>> {
    return this.http.get<Page<AuthoredReview>>(`/api/v1/growers/${encodeURIComponent(username)}/reviews`, {
      params: { page, size: 10 },
    });
  }

  follow(username: string): Observable<void> {
    return this.http.put<void>(`/api/v1/growers/${encodeURIComponent(username)}/follow`, {});
  }

  unfollow(username: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/growers/${encodeURIComponent(username)}/follow`);
  }

  following(): Observable<FollowedGrower[]> {
    return this.http.get<FollowedGrower[]>('/api/v1/me/following');
  }
}

export function avatarUrl(username: string): string {
  return `/api/v1/growers/${encodeURIComponent(username)}/avatar`;
}
