import {
  HttpInterceptorFn,
  provideHttpClient,
  withFetch,
  withInterceptors,
} from '@angular/common/http';
import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { EMPTY } from 'rxjs';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';

/**
 * No build não existe API: as páginas públicas saem com o texto fixo e o que depende de dados (listas do catálogo, por
 * exemplo) fica no estado de carregando até o navegador buscar. Nenhuma chamada sai do build, então nenhum dado entra
 * no HTML público.
 */
const noApiDuringPrerender: HttpInterceptorFn = (request, next) =>
  request.url.startsWith('/api/') ? EMPTY : next(request);

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    provideHttpClient(withFetch(), withInterceptors([noApiDuringPrerender])),
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
