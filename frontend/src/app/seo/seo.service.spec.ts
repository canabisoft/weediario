import { DOCUMENT } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { PUBLIC_PAGES } from './public-pages';
import { SeoTitleStrategy } from './seo-title.strategy';
import { SeoService } from './seo.service';

@Component({ template: '' })
class Blank {}

describe('SeoService', () => {
  let head: HTMLHeadElement;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Blank },
          { path: 'strains', component: Blank },
          { path: 'diarios/:id', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: SeoTitleStrategy },
      ],
    });
    TestBed.inject(SeoService);
    router = TestBed.inject(Router);
    head = TestBed.inject(DOCUMENT).head;
  });

  const content = (selector: string) => head.querySelector(selector)?.getAttribute('content');
  const jsonLd = () =>
    [...head.querySelectorAll('script[type="application/ld+json"]')].map((script) =>
      JSON.parse(script.textContent ?? ''),
    );

  it('gives every public page its own title, description, canonical and share card', async () => {
    await router.navigateByUrl('/strains?q=gelato');
    const page = PUBLIC_PAGES.find((candidate) => candidate.path === '/strains')!;

    expect(TestBed.inject(DOCUMENT).title).toBe(page.title);
    expect(content('meta[name="description"]')).toBe(page.description);
    expect(head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://weediario.com/strains',
    );
    expect(content('meta[property="og:image"]')).toBe('https://weediario.com/og/strains.jpg');
    expect(content('meta[property="og:locale"]')).toBe('pt_BR');
    expect(content('meta[name="twitter:card"]')).toBe('summary_large_image');
    expect(jsonLd().map((data) => data['@type'])).toEqual(['Dataset']);
  });

  it('describes the site, the organization and the FAQ on the home', async () => {
    await router.navigateByUrl('/');

    const types = jsonLd().map((data) => data['@type']);
    expect(types).toEqual(['WebSite', 'Organization', 'FAQPage']);
    const faq = jsonLd().find((data) => data['@type'] === 'FAQPage');
    expect(faq.mainEntity).toHaveLength(4);
    expect(head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://weediario.com/',
    );
  });

  it('keeps pages behind the login out of search engines and share cards', async () => {
    await router.navigateByUrl('/strains');
    await router.navigateByUrl('/diarios/123');

    expect(content('meta[name="robots"]')).toBe('noindex, nofollow');
    expect(head.querySelector('link[rel="canonical"]')).toBeNull();
    expect(head.querySelector('meta[property="og:image"]')).toBeNull();
    expect(jsonLd()).toEqual([]);
  });
});
