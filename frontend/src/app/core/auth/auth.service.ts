import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, tap } from 'rxjs';
import { MyProfile } from '../api/api.models';

interface AccessTokenResponse {
  readonly accessToken: string;
  readonly expiresAt: string;
}

/** Sessão aberta junto com uma chave de acesso nova, que só aparece nesta resposta. */
interface KeyedTokenResponse extends AccessTokenResponse {
  readonly accessKey: string;
}

/** Prefixo das chaves de acesso; o que não começa assim, no login, é a senha de uma conta antiga. */
export const ACCESS_KEY_PREFIX = 'wd_sec_';

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

  /** Login com apelido + chave de acesso ({@code wd_sec_...}). Não existe senha nem e-mail. */
  async login(login: string, accessKey: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<AccessTokenResponse>(`${AUTH}/login`, { login, accessKey }),
    );
    await this.startSession(response.accessToken);
  }

  /**
   * Cria a conta e já entra. Devolve a chave de acesso: o servidor não guarda a chave, então é a única vez que ela
   * aparece.
   *
   * @param recoveryVerifier     derivado da frase de 12 palavras (as palavras nunca são enviadas)
   * @param acceptedTermsVersion versão dos Termos de Uso marcada como lida e aceita no cadastro
   */
  async register(username: string, recoveryVerifier: string, acceptedTermsVersion: string): Promise<string> {
    return this.keyed(this.http.post<KeyedTokenResponse>(`${AUTH}/register`, { username, recoveryVerifier, acceptedTermsVersion }));
  }

  /** Conta anterior à chave de acesso: último login com a senha, que vira uma chave nova (devolvida aqui). */
  async legacyLogin(login: string, password: string): Promise<string> {
    return this.keyed(this.http.post<KeyedTokenResponse>(`${AUTH}/legacy-login`, { login, password }));
  }

  /** Recupera a conta com a frase: gera uma chave nova (devolvida aqui), derruba as outras sessões e já entra. */
  async recover(username: string, recoveryVerifier: string): Promise<string> {
    return this.keyed(this.http.post<KeyedTokenResponse>(`${AUTH}/recover`, { username, recoveryVerifier }));
  }

  /** Gera (conta antiga) ou troca a frase de recuperação; exige a chave de acesso atual. */
  async changeRecoveryPhrase(recoveryVerifier: string, currentAccessKey: string): Promise<void> {
    this.me.set(await firstValueFrom(this.http.put<MyProfile>('/api/v1/me/recovery', { recoveryVerifier, currentAccessKey })));
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

  private async keyed(request: Observable<KeyedTokenResponse>): Promise<string> {
    const response = await firstValueFrom(request);
    await this.startSession(response.accessToken);
    return response.accessKey;
  }

  private async startSession(accessToken: string): Promise<void> {
    this.token.set(accessToken);
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
