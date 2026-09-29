import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { GROWER_RANKING_SOURCE, HttpGrowerRankingSource } from './growers/grower-ranking.source';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Parâmetros de rota, query params e data das rotas chegam como inputs dos componentes de página.
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // Retoma a sessão (cookie HttpOnly) antes da primeira navegação, para os guards já saberem quem está logado.
    provideAppInitializer(() => inject(AuthService).restoreSession()),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    { provide: GROWER_RANKING_SOURCE, useClass: HttpGrowerRankingSource },
  ],
};
