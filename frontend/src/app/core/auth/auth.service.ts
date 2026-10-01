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
  readonly isAdmin = computed(() => this.me()?.role === 'ADMIN');
  /** Conta anterior à frase de recuperação: precisa gerar a frase (sem ela, não há como recuperar a conta). */
  readonly needsRecoveryPhrase = computed(() => !!this.me() && !this.me()!.hasRecoveryPhrase);
  /** Logado, mas ainda sem aceitar a versão vigente dos Termos de Uso (não consegue publicar). */
  readonly needsTermsAcceptance = computed(() => !!this.me() && !this.me()!.termsAccepted);

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

  /** @param login apelido (não existe e-mail) */
  async login(login: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AccessTokenResponse>(`${AUTH}/login`, { login, password }),
    );
    this.token.set(response.accessToken);
    await firstValueFrom(this.loadMe());
  }

  /**
   * @param recoveryVerifier     derivado da frase de 12 palavras (as palavras nunca são enviadas)
   * @param acceptedTermsVersion versão dos Termos de Uso marcada como lida e aceita no cadastro
   */
  async register(username: string, password: string, recoveryVerifier: string, acceptedTermsVersion: string): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${AUTH}/register`, { username, password, recoveryVerifier, acceptedTermsVersion }),
    );
    await this.login(username, password);
  }

  /** Recupera a conta com a frase: define a senha nova, derruba as outras sessões e já entra. */
  async recover(username: string, recoveryVerifier: string, newPassword: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AccessTokenResponse>(`${AUTH}/recover`, { username, recoveryVerifier, newPassword }),
    );
    this.token.set(response.accessToken);
    await firstValueFrom(this.loadMe());
  }

  /** Gera (conta antiga) ou troca a frase de recuperação; exige a senha atual. */
  async changeRecoveryPhrase(recoveryVerifier: string, currentPassword: string): Promise<void> {
    this.me.set(await firstValueFrom(this.http.put<MyProfile>('/api/v1/me/recovery', { recoveryVerifier, currentPassword })));
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

  /** Aceite da versão vigente dos Termos de Uso; renova o token para a API liberar publicações. */
  async acceptTerms(version: string): Promise<void> {
    const profile = await firstValueFrom(this.http.put<MyProfile>('/api/v1/me/terms', { version }));
    this.me.set(profile);
    await firstValueFrom(this.refresh());
  }

  updateMe(profile: MyProfile): void {
    this.me.set(profile);
  }

  clear(): void {
    this.token.set(null);
    this.me.set(null);
  }
}
