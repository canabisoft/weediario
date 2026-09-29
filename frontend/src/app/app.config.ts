import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { provideServiceWorker } from '@angular/service-worker';
import { GROWER_RANKING_SOURCE, SampleGrowerRankingSource } from './growers/grower-ranking.source';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Query params e data das rotas chegam como inputs dos componentes de página.
    provideRouter(routes, withComponentInputBinding()),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
    // Trocar por uma implementação HTTP quando o backend expuser o ranking.
    { provide: GROWER_RANKING_SOURCE, useClass: SampleGrowerRankingSource },
  ],
};
