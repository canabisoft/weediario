import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface AccountMetrics {
  readonly total: number;
  readonly active: number;
  readonly suspended: number;
  readonly banned: number;
  readonly moderators: number;
  readonly admins: number;
}

export interface ContentMetrics {
  readonly publicDiaries: number;
  readonly privateDiaries: number;
  readonly diaryUpdates: number;
  readonly comments: number;
  readonly publicRecipes: number;
  readonly recipeComments: number;
  readonly reviews: number;
}

export interface ActivityMetrics {
  readonly newAccounts: number;
  readonly newDiaries: number;
  readonly diaryUpdates: number;
  readonly comments: number;
  readonly reviews: number;
  readonly recipes: number;
  /** Contas distintas que publicaram algo no período (só o número). */
  readonly activeGrowers: number;
}

/** Números da plataforma, só agregados: nada por pessoa e nada do acompanhamento de tratamento. */
export interface AdminDashboard {
  readonly generatedAt: string;
  readonly accounts: AccountMetrics;
  readonly content: ContentMetrics;
  readonly published: number;
  readonly last7Days: ActivityMetrics;
  readonly last30Days: ActivityMetrics;
  readonly moderation: { readonly pending: number; readonly held: number; readonly decisionsLast30Days: number };
}

export interface TeamMember {
  readonly username: string;
  readonly role: 'MODERATOR' | 'ADMIN';
  readonly status: 'ACTIVE' | 'SUSPENDED' | 'BANNED';
}

/** Painel do admin: métricas e gestão da equipe. O backend devolve 403 para qualquer outro papel. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  metrics(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>('/api/v1/admin/metrics');
  }

  team(): Observable<TeamMember[]> {
    return this.http.get<TeamMember[]>('/api/v1/admin/team');
  }

  promote(username: string, note?: string): Observable<TeamMember> {
    return this.http.post<TeamMember>(`/api/v1/admin/team/${encodeURIComponent(username)}/promote`, { note });
  }

  removeFromTeam(username: string, note?: string): Observable<void> {
    return this.http.post<void>(`/api/v1/admin/team/${encodeURIComponent(username)}/remove`, { note });
  }
}
