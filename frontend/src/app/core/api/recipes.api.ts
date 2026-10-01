import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';

export type RecipeKind = 'MINERAL' | 'ORGANIC';
export type RecipeApplication = 'FERTIGATION' | 'SOIL_MIX' | 'TEA' | 'FOLIAR' | 'TOP_DRESS';
export type RecipeStage = 'VEGETATIVE' | 'FLOWERING' | 'ALL';
export type RecipeVisibility = 'PUBLIC' | 'PRIVATE';
export type IngredientUnit = 'G' | 'MG' | 'KG' | 'ML' | 'L' | 'TSP' | 'TBSP' | 'UNIT';
export type RecipeSort = 'RECENT' | 'MOST_REPLICATED' | 'MOST_USED';

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

  ofDiary(diaryId: string): Observable<RecipeSummary[]> {
    return this.http.get<RecipeSummary[]>(`/api/v1/diaries/${diaryId}/recipes`);
  }

  setDiaryRecipes(diaryId: string, recipeIds: string[]): Observable<RecipeSummary[]> {
    return this.http.put<RecipeSummary[]>(`/api/v1/diaries/${diaryId}/recipes`, { recipeIds });
  }
}
