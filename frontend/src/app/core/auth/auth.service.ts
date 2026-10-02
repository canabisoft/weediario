import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { MyProfile } from '../api/api.models';

interface AccessTokenResponse {
  readonly accessToken: string;
  readonly expiresAt: string;
}

/** Prefixo das chaves de acesso antigas: quem digita isso no lugar da senha ainda precisa criar uma senha. */
export const ACCESS_KEY_PREFIX = 'wd_sec_';
/** Mesmo mínimo do backend (PasswordPolicy.MIN_LENGTH). */
export const PASSWORD_MIN_LENGTH = 10;
export const PASSWORD_MAX_LENGTH = 128;

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
  /** Conta anterior à frase de recuperação: precisa gerar a frase (sem ela e sem e-mail, não há como recuperar). */
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

  async login(login: string, password: string): Promise<void> {
    await this.session(this.http.post<AccessTokenResponse>(`${AUTH}/login`, { login, password }));
  }

  /** Conta que só tinha a chave de acesso antiga: entra com ela uma última vez e cria a senha. */
  async loginWithAccessKey(login: string, accessKey: string, newPassword: string): Promise<void> {
    await this.session(this.http.post<AccessTokenResponse>(`${AUTH}/access-key-login`, { login, accessKey, newPassword }));
  }

  /**
   * Cria a conta e já entra.
   *
   * @param email                opcional; só serve para recuperar a senha e receber avisos de segurança
   * @param recoveryVerifier     derivado da frase de 12 palavras (as palavras nunca são enviadas)
   * @param acceptedTermsVersion versão dos Termos de Uso marcada como lida e aceita no cadastro
   */
  async register(
    username: string,
    password: string,
    email: string | null,
    recoveryVerifier: string,
    acceptedTermsVersion: string,
  ): Promise<void> {
    await this.session(
      this.http.post<AccessTokenResponse>(`${AUTH}/register`, { username, password, email, recoveryVerifier, acceptedTermsVersion }),
    );
  }

  /**
   * Recupera a conta com a frase: define a senha nova, troca a frase por outra (a antiga foi digitada e pode ter sido
   * vista), encerra as outras sessões e já entra.
   */
  async recoverWithPhrase(username: string, recoveryVerifier: string, newPassword: string, newRecoveryVerifier: string): Promise<void> {
    await this.session(
      this.http.post<AccessTokenResponse>(`${AUTH}/recover`, { username, recoveryVerifier, newPassword, newRecoveryVerifier }),
    );
  }

  /** Pede o link de redefinição. A resposta é sempre a mesma, tenha a conta e-mail ou não. */
  async requestPasswordReset(username: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${AUTH}/password-reset`, { username }));
  }

  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await this.session(this.http.post<AccessTokenResponse>(`${AUTH}/password-reset/confirm`, { token, newPassword }));
  }

  async verifyEmail(token: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${AUTH}/email/verify`, { token }));
  }

  /** Gera (conta antiga) ou troca a frase de recuperação; exige a senha atual. */
  async changeRecoveryPhrase(recoveryVerifier: string, currentPassword: string): Promise<void> {
    this.me.set(await firstValueFrom(this.http.put<MyProfile>('/api/v1/me/recovery', { recoveryVerifier, currentPassword })));
  }

  /** Troca a senha. O servidor encerra todas as sessões e devolve uma nova para este aparelho. */
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await this.session(this.http.put<AccessTokenResponse>('/api/v1/me/password', { currentPassword, newPassword }));
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

  private async session(request: Observable<AccessTokenResponse>): Promise<void> {
    const response = await firstValueFrom(request);
    this.token.set(response.accessToken);
    await firstValueFrom(this.loadMe());
  }

  updateMe(profile: MyProfile): void {
    this.me.set(profile);
  }

  clear(): void {
    this.token.set(null);
    this.me.set(null);
  }
}
