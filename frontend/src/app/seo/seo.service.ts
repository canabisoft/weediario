import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta } from '@angular/platform-browser';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { PublicPage, SITE_NAME, SITE_URL, publicPage } from './public-pages';
import { structuredDataFor } from './structured-data';

const OG_WIDTH = '1200';
const OG_HEIGHT = '630';
const JSON_LD_ATTR = 'data-seo';

/**
 * Head de cada navegação (o title fica com o SeoTitleStrategy): nas páginas públicas, description, canonical, Open Graph, Twitter card e JSON-LD; nas
 * outras (que pedem conta ou mostram dado de usuário), noindex e nada de canonical, og:* ou JSON-LD.
 * <p>
 * Roda também na pré-renderização do build, então o HTML pronto já sai com o head certo para robôs sem JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly document = inject(DOCUMENT);
  private readonly meta = inject(Meta);
  private readonly router = inject(Router);

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => this.apply(pathOf(event.urlAfterRedirects)));
  }

  private apply(path: string): void {
    const page = publicPage(path);
    this.removeStructuredData();
    if (!page) {
      this.applyPrivate();
      return;
    }
    const url = `${SITE_URL}${page.path === '/' ? '/' : page.path}`;
    const image = `${SITE_URL}${page.ogImage}`;
    this.setName('description', page.description);
    this.setName('robots', 'index, follow');
    this.setCanonical(url);
    this.setProperty('og:type', 'website');
    this.setProperty('og:locale', 'pt_BR');
    this.setProperty('og:site_name', SITE_NAME);
    this.setProperty('og:title', page.title);
    this.setProperty('og:description', page.description);
    this.setProperty('og:url', url);
    this.setProperty('og:image', image);
    this.setProperty('og:image:width', OG_WIDTH);
    this.setProperty('og:image:height', OG_HEIGHT);
    this.setProperty('og:image:alt', ogAlt(page));
    this.setName('twitter:card', 'summary_large_image');
    this.setName('twitter:title', page.title);
    this.setName('twitter:description', page.description);
    this.setName('twitter:image', image);
    for (const data of structuredDataFor(page.path)) {
      this.addStructuredData(data);
    }
  }

  /** Página que pede conta: o título vem da rota; robôs não indexam e não há metadados de compartilhamento. */
  private applyPrivate(): void {
    this.setName('robots', 'noindex, nofollow');
    this.document.head.querySelector('link[rel="canonical"]')?.remove();
    for (const property of [
      'og:type',
      'og:locale',
      'og:site_name',
      'og:title',
      'og:description',
      'og:url',
      'og:image',
      'og:image:width',
      'og:image:height',
      'og:image:alt',
    ]) {
      this.meta.removeTag(`property="${property}"`);
    }
    for (const name of ['twitter:card', 'twitter:title', 'twitter:description', 'twitter:image']) {
      this.meta.removeTag(`name="${name}"`);
    }
  }

  private setName(name: string, content: string): void {
    this.meta.updateTag({ name, content });
  }

  private setProperty(property: string, content: string): void {
    this.meta.updateTag({ property, content });
  }

  private setCanonical(href: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  /** application/ld+json não é executado pelo navegador: a CSP (script-src 'self') não precisa liberar nada. */
  private addStructuredData(data: object): void {
    const script = this.document.createElement('script');
    script.setAttribute('type', 'application/ld+json');
    script.setAttribute(JSON_LD_ATTR, '');
    // "<" escapado: o texto nunca fecha a tag <script> antes da hora.
    script.textContent = JSON.stringify(data).replace(/</g, '\\u003c');
    this.document.head.appendChild(script);
  }

  private removeStructuredData(): void {
    this.document.head
      .querySelectorAll(`script[${JSON_LD_ATTR}]`)
      .forEach((script) => script.remove());
  }
}

function pathOf(url: string): string {
  const path = url.split(/[?#]/)[0] || '/';
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

function ogAlt(page: PublicPage): string {
  return page.path === '/'
    ? 'Flor de cannabis em floração, com o nome weediário'
    : `${page.title.split(/ [—·] /)[0]} no weediário`;
}
