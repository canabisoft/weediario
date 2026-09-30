import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { MyProfile } from '../api/api.models';

interface AccessTokenResponse {
  readonly accessToken: string;
  readonly expiresAt: string;
}

const AUTH = '/api/v1/auth';

/**
 * Sessão do usuário.
 * <p>
 * O access token vive <strong>só em memória</strong> (nunca em localStorage/sessionStorage, que um XSS leria).
 * Ao recarregar a página, a sessão é retomada pelo refresh token, que está num cookie HttpOnly inacessível ao JS.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly token = signal<string | null>(null);
  readonly me = signal<MyProfile | null>(null);
  readonly isLoggedIn = computed(() => this.me() !== null);
  readonly isModerator = computed(() => ['MODERATOR', 'ADMIN'].includes(this.me()?.role ?? ''));

  private refreshInFlight: Observable<string | null> | null = null;

  accessToken(): string | null {
    return this.token();
  }

  /** Chamado na inicialização do app: tenta retomar a sessão pelo cookie, sem falhar se não houver. */
  async restoreSession(): Promise<void> {
    const token = await firstValueFrom(this.refresh());
    if (token) {
      await firstValueFrom(this.loadMe()).catch(() => this.clear());
    }
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AccessTokenResponse>(`${AUTH}/login`, { email, password }),
    );
    this.token.set(response.accessToken);
    await firstValueFrom(this.loadMe());
  }

  async register(username: string, email: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${AUTH}/register`, { username, email, password }));
    await this.login(email, password);
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>(`${AUTH}/logout`, {}));
    } finally {
      this.clear();
    }
  }

  /**
   * Troca o refresh token (cookie) por um novo access token. Chamadas simultâneas compartilham a mesma
   * requisição, para não rotacionar o refresh token duas vezes (o que o backend trataria como roubo).
   */
  refresh(): Observable<string | null> {
    this.refreshInFlight ??= this.http.post<AccessTokenResponse>(`${AUTH}/refresh`, {}).pipe(
      map((response) => response.accessToken),
      tap((token) => this.token.set(token)),
      catchError(() => {
        this.clear();
        return of(null);
      }),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  loadMe(): Observable<MyProfile> {
    return this.http.get<MyProfile>('/api/v1/me').pipe(tap((profile) => this.me.set(profile)));
  }

  updateMe(profile: MyProfile): void {
    this.me.set(profile);
  }

  clear(): void {
    this.token.set(null);
    this.me.set(null);
  }
}
