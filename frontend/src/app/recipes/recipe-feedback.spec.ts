import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { RecipeFeedback } from './recipe-feedback';
import { RecommendedRecipes } from './recommended-recipes';

const summary = (id: string, extra: object = {}) => ({
  id,
  name: 'Receita ' + id,
  kind: 'MINERAL',
  application: 'FERTIGATION',
  author: 'fulano',
  visibility: 'PUBLIC',
  batchLiters: 10,
  ingredientNames: ['MKP'],
  ingredientCount: 1,
  replications: 0,
  diaries: 0,
  updatedAt: '2026-09-30T10:00:00Z',
  ratingCount: 0,
  ...extra,
});

function providers(loggedIn: boolean) {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    provideRouter([]),
    { provide: AuthService, useValue: { isLoggedIn: () => loggedIn, isModerator: () => false } },
  ];
}

describe('RecipeFeedback', () => {
  it('lets a grower recommend the recipe with stars and shows the new average', () => {
    TestBed.configureTestingModule({ providers: providers(true) });
    const fixture = TestBed.createComponent(RecipeFeedback);
    fixture.componentRef.setInput('recipeId', 'r1');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/recipes/r1/rating').flush({ count: 0, canRate: true });
    http.expectOne((req) => req.url === '/api/v1/recipes/r1/comments').flush({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    fixture.detectChanges();

    (fixture.nativeElement.querySelectorAll('input[type="radio"]')[3] as HTMLInputElement).click();
    const put = http.expectOne({ method: 'PUT', url: '/api/v1/recipes/r1/rating' });
    expect(put.request.body).toEqual({ stars: 4 });
    put.flush({ average: 4, count: 1, mine: 4, canRate: true });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('4,0');
    expect(fixture.nativeElement.textContent).toContain('1 avaliação');
    expect(fixture.nativeElement.textContent).toContain('Remover minha nota');
  });

  it('shows comments and appends a new one after posting', () => {
    TestBed.configureTestingModule({ providers: providers(true) });
    const fixture = TestBed.createComponent(RecipeFeedback);
    fixture.componentRef.setInput('recipeId', 'r1');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/recipes/r1/rating').flush({ count: 0, canRate: false });
    http.expectOne((req) => req.url === '/api/v1/recipes/r1/comments').flush({
      items: [{ id: 'c1', author: 'ana', body: 'Usei em coco', createdAt: '2026-09-29T10:00:00Z', hidden: false, canDelete: false, canReport: true }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Usei em coco');
    expect(fixture.nativeElement.textContent).toContain('Denunciar');

    (fixture.componentInstance as unknown as { draft: string }).draft = 'Qual EC na flora?';
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const post = http.expectOne({ method: 'POST', url: '/api/v1/recipes/r1/comments' });
    expect(post.request.body).toEqual({ body: 'Qual EC na flora?' });
    post.flush({ id: 'c2', author: 'eu', body: 'Qual EC na flora?', createdAt: '2026-09-30T10:00:00Z', hidden: false, canDelete: true, canReport: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('li').length).toBe(2);
  });

  it('shows replies under their comment, replies inline and reacts anonymously', () => {
    TestBed.configureTestingModule({ providers: providers(true) });
    const fixture = TestBed.createComponent(RecipeFeedback);
    fixture.componentRef.setInput('recipeId', 'r1');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/recipes/r1/rating').flush({ count: 0, canRate: false });
    http.expectOne((req) => req.url === '/api/v1/recipes/r1/comments').flush({
      items: [
        {
          id: 'c1', author: 'ana', body: 'Usei em coco', createdAt: '2026-09-29T10:00:00Z', hidden: false, canDelete: false, canReport: true,
          reactions: { counts: { FIRE: 2, HERB: 0, CLAP: 0 }, mine: [] },
          replyCount: 3,
          replies: [
            { id: 'c2', parentId: 'c1', author: 'bia', body: 'Eu também', createdAt: '2026-09-29T11:00:00Z', hidden: false, canDelete: false, canReport: true },
          ],
        },
      ],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[aria-label="Respostas ao comentário de ana"]')?.textContent).toContain('Eu também');
    expect(element.textContent).toContain('Ver mais 2 respostas');

    // Responder uma resposta: o campo abre debaixo do comentário raiz e o pedido leva o id respondido.
    (element.querySelector('[aria-label="Responder bia"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Respondendo a bia');
    (fixture.componentInstance as unknown as { replyDraft: string }).replyDraft = 'Qual EC?';
    (element.querySelector('textarea#recipe-reply-c1')!.closest('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    const post = http.expectOne({ method: 'POST', url: '/api/v1/recipes/r1/comments' });
    expect(post.request.body).toEqual({ body: 'Qual EC?', parentId: 'c2' });
    post.flush({ id: 'c3', parentId: 'c1', author: 'eu', body: 'Qual EC?', createdAt: '2026-09-30T10:00:00Z', hidden: false, canDelete: true, canReport: false });
    fixture.detectChanges();
    expect(element.querySelector('[aria-label="Respostas ao comentário de ana"]')?.textContent).toContain('Qual EC?');
    expect(element.textContent).not.toContain('Respondendo a');

    // Reação anônima: só contagens; a própria fica marcada.
    const fire = element.querySelector('[aria-label="Fogo: 2"]') as HTMLButtonElement;
    expect(fire.getAttribute('aria-pressed')).toBe('false');
    fire.click();
    const put = http.expectOne({ method: 'PUT', url: '/api/v1/recipes/r1/comments/c1/reactions/FIRE' });
    put.flush({ counts: { FIRE: 3, HERB: 0, CLAP: 0 }, mine: ['FIRE'] });
    fixture.detectChanges();
    expect(element.querySelector('[aria-label="Fogo: 3"]')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('shows reaction counts without buttons to visitors', () => {
    TestBed.configureTestingModule({ providers: providers(false) });
    const fixture = TestBed.createComponent(RecipeFeedback);
    fixture.componentRef.setInput('recipeId', 'r1');
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/recipes/r1/rating').flush({ count: 0, canRate: false });
    http.expectOne((req) => req.url === '/api/v1/recipes/r1/comments').flush({
      items: [
        {
          id: 'c1', author: 'ana', body: 'Usei em coco', createdAt: '2026-09-29T10:00:00Z', hidden: false, canDelete: false, canReport: false,
          reactions: { counts: { FIRE: 0, HERB: 4, CLAP: 0 }, mine: [] },
        },
      ],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[aria-label="Erva: 4"]')?.tagName).toBe('SPAN');
    expect(element.querySelector('[aria-label="Fogo: 0"]')).toBeNull();
    expect(element.textContent).not.toContain('Responder');
  });
});

describe('RecommendedRecipes', () => {
  it('explains why each recipe is suggested and offers to use it in the grow', () => {
    TestBed.configureTestingModule({ providers: providers(true) });
    const fixture = TestBed.createComponent(RecommendedRecipes);
    fixture.componentRef.setInput('diaryId', 'd1');
    fixture.componentRef.setInput('actionLabel', 'Usar neste cultivo');
    const picked: string[] = [];
    fixture.componentInstance.picked.subscribe((recipe) => picked.push(recipe.id));
    fixture.detectChanges();

    const request = TestBed.inject(HttpTestingController).expectOne((req) => req.url === '/api/v1/recipes/recommended');
    expect(request.request.params.get('diaryId')).toBe('d1');
    request.flush([
      { recipe: summary('a'), sharedProducts: 2 },
      { recipe: summary('b', { ratingCount: 5, rating: 4.6 }), sharedProducts: 0 },
    ]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Usa 2 produtos que você já tem');
    expect(text).toContain('Bem avaliada pela comunidade');
    (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])[0].click();
    expect(picked).toEqual(['a']);
  });
});
