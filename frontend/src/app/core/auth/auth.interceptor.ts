import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Anexa o access token às chamadas da API e, se ele tiver expirado (401), renova a sessão uma vez
 * e repete a requisição. Endpoints de autenticação usam só o cookie e nunca levam o Bearer.
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
      return auth.refresh().pipe(
        switchMap((renewed) => (renewed ? next(withToken(request, renewed)) : throwError(() => error))),
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
