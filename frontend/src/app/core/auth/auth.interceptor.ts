import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Anexa o access token às chamadas da API e, se ele tiver expirado (401), renova a sessão uma vez
 * e repete a requisição. Endpoints de autenticação usam só o cookie e nunca levam o Bearer.
 * <p>
 * Conta suspensa ou banida (401 {@code ACCOUNT_DISABLED}): a renovação falha, porque as sessões já foram revogadas,
 * e o app sai da conta.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApi(request) || isAuthEndpoint(request)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const token = auth.accessToken();

  return next(withToken(request, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !token) {
        return throwError(() => error);
      }
      // SESSION_OUTDATED: o papel da conta mudou (entrou ou saiu da equipe). Renova e recarrega o perfil.
      const outdated = (error.error as { code?: string } | null)?.code === 'SESSION_OUTDATED';
      return auth.refresh().pipe(
        switchMap((renewed) => {
          if (!renewed) {
            return throwError(() => error);
          }
          if (outdated) {
            auth.loadMe().subscribe({ error: () => undefined });
          }
          return next(withToken(request, renewed));
        }),
      );
    }),
  );
};

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

function isApi(request: HttpRequest<unknown>): boolean {
  return request.url.startsWith('/api/');
}

function isAuthEndpoint(request: HttpRequest<unknown>): boolean {
  return request.url.startsWith('/api/v1/auth/');
}
