import { RenderMode, ServerRoute } from '@angular/ssr';
import { PUBLIC_PAGES } from './seo/public-pages';

/**
 * Só as páginas públicas viram HTML pronto no build (robôs sem JavaScript leem o conteúdo). Todo o resto, inclusive o que
 * mostra dado de usuário, continua renderizado só no navegador, a partir do index.csr.html.
 */
export const serverRoutes: ServerRoute[] = [
  ...PUBLIC_PAGES.map((page): ServerRoute => ({
    path: page.path.slice(1),
    renderMode: RenderMode.Prerender,
  })),
  { path: '**', renderMode: RenderMode.Client },
];
