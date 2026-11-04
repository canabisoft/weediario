import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { publicPage } from './public-pages';

/**
 * Páginas públicas usam o título com palavra-chave de public-pages.ts; as outras, o `title` da própria rota. Sem isto o
 * TitleStrategy padrão do Angular escreveria o título da rota por cima, depois do SeoService.
 */
@Injectable({ providedIn: 'root' })
export class SeoTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const path = snapshot.url.split(/[?#]/)[0] || '/';
    const title = publicPage(path)?.title ?? this.buildTitle(snapshot);
    if (title) {
      this.title.setTitle(title);
    }
  }
}
