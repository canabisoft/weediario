import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';

export type RecipeKind = 'MINERAL' | 'ORGANIC';
export type RecipeApplication = 'FERTIGATION' | 'SOIL_MIX' | 'TEA' | 'FOLIAR' | 'TOP_DRESS';
export type RecipeStage = 'VEGETATIVE' | 'FLOWERING' | 'ALL';
export type RecipeVisibility = 'PUBLIC' | 'PRIVATE';
export type IngredientUnit = 'G' | 'MG' | 'KG' | 'ML' | 'L' | 'TSP' | 'TBSP' | 'UNIT';
export type RecipeSort = 'RECENT' | 'MOST_REPLICATED' | 'MOST_USED' | 'TOP_RATED';

export interface RecipeSummary {
  readonly id: string;
  readonly name: string;
  readonly kind: RecipeKind;
  readonly application: RecipeApplication;
  readonly stage?: RecipeStage;
  readonly author: string;
  readonly visibility: RecipeVisibility;
  readonly batchLiters: number;
  /** Até 4 primeiros ingredientes, para o card. */
  readonly ingredientNames: string[];
  readonly ingredientCount: number;
  readonly replications: number;
  readonly diaries: number;
  readonly updatedAt: string;
  readonly ratingCount: number;
  /** Média de 1 a 5 (ausente sem avaliações). */
  readonly rating?: number;
}

export interface RecipeIngredient {
  /** Produto de marca do catálogo; ausente em sal/insumo genérico. */
  readonly productId?: string;
  readonly name: string;
  readonly brand?: string;
  readonly quantity: number;
  readonly unit: IngredientUnit;
}

export interface RecipeView {
  readonly id: string;
  readonly name: string;
  readonly kind: RecipeKind;
  readonly application: RecipeApplication;
  readonly stage?: RecipeStage;
  readonly batchLiters: number;
  readonly targetEc?: number;
  readonly targetPh?: number;
  readonly notes?: string;
  readonly visibility: RecipeVisibility;
  readonly author: string;
  readonly ingredients: RecipeIngredient[];
  readonly replications: number;
  readonly diaries: number;
  /** Receita original (só enquanto ela for pública). */
  readonly replicatedFrom?: { readonly id: string; readonly name: string; readonly author: string };
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly ratingCount: number;
  readonly rating?: number;
}

export interface RecipeRating {
  readonly average?: number;
  readonly count: number;
  /** Nota de quem está vendo, se já avaliou. */
  readonly mine?: number;
  /** Logado, não é o autor e a receita é pública. */
  readonly canRate: boolean;
}

export interface RecipeComment {
  readonly id: string;
  readonly author: string;
  readonly body: string;
  readonly createdAt: string;
  /** Ocultado por denúncias (só moderadores veem). */
  readonly hidden: boolean;
  readonly canDelete: boolean;
  readonly canReport: boolean;
}

/** {@code sharedProducts}: produtos de marca da receita que o grower já usa. */
export interface RecipeRecommendation {
  readonly recipe: RecipeSummary;
  readonly sharedProducts: number;
}

export interface RecipeInput {
  readonly name: string;
  readonly kind: RecipeKind;
  readonly application: RecipeApplication;
  readonly stage?: RecipeStage;
  readonly batchLiters: number;
  readonly targetEc?: number;
  readonly targetPh?: number;
  readonly notes?: string;
  readonly visibility: RecipeVisibility;
  readonly ingredients: {
    readonly productId?: string;
    readonly name?: string;
    readonly brand?: string;
    readonly quantity: number;
    readonly unit: IngredientUnit;
  }[];
}

export interface RecipeQuery {
  readonly kind?: RecipeKind;
  readonly q?: string;
  readonly productId?: string;
  readonly sort?: RecipeSort;
  readonly page?: number;
  readonly size?: number;
}

@Injectable({ providedIn: 'root' })
export class RecipesApi {
  private readonly http = inject(HttpClient);

  explore(query: RecipeQuery): Observable<Page<RecipeSummary>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<RecipeSummary>>('/api/v1/recipes', { params });
  }

  mine(page = 0): Observable<Page<RecipeSummary>> {
    return this.http.get<Page<RecipeSummary>>('/api/v1/me/recipes', { params: { page, size: 50 } });
  }

  ofGrower(username: string, page = 0): Observable<Page<RecipeSummary>> {
    return this.http.get<Page<RecipeSummary>>(`/api/v1/growers/${encodeURIComponent(username)}/recipes`, { params: { page } });
  }

  get(id: string): Observable<{ recipe: RecipeView; ownedByViewer: boolean }> {
    return this.http.get<{ recipe: RecipeView; ownedByViewer: boolean }>(`/api/v1/recipes/${id}`);
  }

  create(recipe: RecipeInput): Observable<RecipeView> {
    return this.http.post<RecipeView>('/api/v1/recipes', recipe);
  }

  update(id: string, recipe: RecipeInput): Observable<RecipeView> {
    return this.http.put<RecipeView>(`/api/v1/recipes/${id}`, recipe);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/recipes/${id}`);
  }

  /** Copia a receita pública para as minhas receitas (a cópia nasce privada). */
  replicate(id: string): Observable<RecipeView> {
    return this.http.post<RecipeView>(`/api/v1/recipes/${id}/replicate`, {});
  }

  suggestIngredients(q: string): Observable<string[]> {
    return this.http.get<string[]>('/api/v1/recipes/ingredients/suggest', { params: { q } });
  }

  recommended(options: { diaryId?: string; kind?: RecipeKind; size?: number } = {}): Observable<RecipeRecommendation[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(options)) {
      if (value !== undefined) {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<RecipeRecommendation[]>('/api/v1/recipes/recommended', { params });
  }

  rating(id: string): Observable<RecipeRating> {
    return this.http.get<RecipeRating>(`/api/v1/recipes/${id}/rating`);
  }

  rate(id: string, stars: number): Observable<RecipeRating> {
    return this.http.put<RecipeRating>(`/api/v1/recipes/${id}/rating`, { stars });
  }

  removeRating(id: string): Observable<RecipeRating> {
    return this.http.delete<RecipeRating>(`/api/v1/recipes/${id}/rating`);
  }

  comments(id: string, page = 0): Observable<Page<RecipeComment>> {
    return this.http.get<Page<RecipeComment>>(`/api/v1/recipes/${id}/comments`, { params: { page } });
  }

  comment(id: string, body: string): Observable<RecipeComment> {
    return this.http.post<RecipeComment>(`/api/v1/recipes/${id}/comments`, { body });
  }

  deleteComment(id: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/recipes/${id}/comments/${commentId}`);
  }

  reportComment(id: string, commentId: string): Observable<void> {
    return this.http.post<void>(`/api/v1/recipes/${id}/comments/${commentId}/report`, {});
  }

  ofDiary(diaryId: string): Observable<RecipeSummary[]> {
    return this.http.get<RecipeSummary[]>(`/api/v1/diaries/${diaryId}/recipes`);
  }

  setDiaryRecipes(diaryId: string, recipeIds: string[]): Observable<RecipeSummary[]> {
    return this.http.put<RecipeSummary[]>(`/api/v1/diaries/${diaryId}/recipes`, { recipeIds });
  }
}
