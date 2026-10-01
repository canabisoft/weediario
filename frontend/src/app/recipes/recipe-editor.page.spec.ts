import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RecipeEditorPage } from './recipe-editor.page';

type Editor = {
  name: string;
  batchLiters: number | null;
  targetEc: number | null;
  setKind(kind: 'MINERAL' | 'ORGANIC'): void;
  patch(index: number, changes: object): void;
  add(): void;
  save(): void;
  error(): string | null;
};

describe('RecipeEditorPage', () => {
  function setup(diario?: string) {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    const fixture = TestBed.createComponent(RecipeEditorPage);
    if (diario) {
      fixture.componentRef.setInput('diario', diario);
    }
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    return { fixture, editor: fixture.componentInstance as unknown as Editor, http: TestBed.inject(HttpTestingController), navigate };
  }

  it('sends generic salts by name and branded products only by catalog id', () => {
    const { editor, http, navigate } = setup();
    editor.name = 'Flora semana 3';
    editor.targetEc = 1.8;
    editor.patch(0, { name: 'Nitrato de cálcio', quantity: 8.5 });
    editor.add();
    editor.patch(1, { mode: 'brand', product: { id: 'p1', name: 'Flora Micro', brand: 'GHE' }, quantity: 15, unit: 'ML' });

    editor.save();

    const request = http.expectOne('/api/v1/recipes');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toMatchObject({
      name: 'Flora semana 3',
      kind: 'MINERAL',
      application: 'FERTIGATION',
      batchLiters: 10,
      targetEc: 1.8,
      visibility: 'PUBLIC',
      ingredients: [
        { name: 'Nitrato de cálcio', quantity: 8.5, unit: 'G' },
        { productId: 'p1', quantity: 15, unit: 'ML' },
      ],
    });
    expect(request.request.body.ingredients[1].name).toBeUndefined();
    request.flush({ id: 'r1' });
    expect(navigate).toHaveBeenCalledWith(['/receitas', 'r1']);
  });

  it('switching to organic suggests a soil mix in kilos and drops EC', () => {
    const { editor, http } = setup();
    editor.targetEc = 1.2;
    editor.setKind('ORGANIC');
    editor.name = 'Super solo';
    editor.patch(0, { name: 'Bokashi', quantity: 2 });

    editor.save();

    const body = http.expectOne('/api/v1/recipes').request.body;
    expect(body.application).toBe('SOIL_MIX');
    expect(body.batchLiters).toBe(100);
    expect(body.targetEc).toBeUndefined();
    expect(body.ingredients[0].unit).toBe('KG');
  });

  it('does not send an incomplete ingredient', () => {
    const { editor, http } = setup();
    editor.name = 'Incompleta';
    editor.patch(0, { mode: 'brand', quantity: 1 });

    editor.save();

    expect(editor.error()).toContain('Escolha a marca e o produto');
    http.expectNone('/api/v1/recipes');
  });

  it('adds a new recipe to the diary it was created from, keeping the ones already there', () => {
    const { editor, http, navigate } = setup('d1');
    editor.name = 'Vega';
    editor.patch(0, { name: 'MKP', quantity: 1 });

    editor.save();
    http.expectOne('/api/v1/recipes').flush({ id: 'new' });
    http.expectOne({ method: 'GET', url: '/api/v1/diaries/d1/recipes' }).flush([{ id: 'old' }]);
    const attach = http.expectOne({ method: 'PUT', url: '/api/v1/diaries/d1/recipes' });
    expect(attach.request.body).toEqual({ recipeIds: ['old', 'new'] });
    attach.flush([]);

    expect(navigate).toHaveBeenCalledWith(['/diarios', 'd1']);
  });
});
