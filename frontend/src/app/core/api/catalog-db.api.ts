import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from './api.models';
import { compact } from './diaries.api';

/** Tipos de item do catálogo da comunidade. */
export type CatalogKind = 'strains' | 'breeders' | 'brands' | 'products';
export type ProductCategory = 'NUTRIENT' | 'ADDITIVE' | 'SUBSTRATE' | 'PEST_CONTROL' | 'OTHER';
export type Dominance = 'INDICA' | 'INDICA_DOMINANT' | 'HYBRID' | 'SATIVA_DOMINANT' | 'SATIVA';
export type FloweringSpeed = 'SHORT' | 'MEDIUM' | 'LONG';
export type SeedKind = 'FEMINIZED' | 'REGULAR';
/** BREEDER: semente cadastrada pelo próprio breeder (programa de breeders). */
export type CatalogSource = 'DATASET' | 'COMMUNITY' | 'BREEDER';
/** TESTING: semente de breeder em teste pela comunidade, ainda fora do banco oficial (só strains). */
export type CatalogStatus = 'UNVERIFIED' | 'VERIFIED' | 'TESTING';
export type RankingKind = 'STRAINS' | 'BREEDERS' | 'BRANDS' | 'PRODUCTS';

/** Sugestão do autocomplete ou item resolvido. {@code parent*} = breeder da strain ou marca do produto. */
export interface CatalogItem {
  readonly id: string;
  readonly name: string;
  readonly parentId?: string;
  readonly parentName?: string;
  readonly category?: ProductCategory;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly usage?: number;
}

export interface Resolution {
  readonly outcome: 'EXISTING' | 'SIMILAR' | 'CREATED';
  readonly item?: CatalogItem;
  readonly candidates: CatalogItem[];
}

export interface StrainListItem {
  readonly id: string;
  readonly name: string;
  readonly breeder?: string;
  readonly breederId?: string;
  readonly floweringType?: 'PHOTOPERIOD' | 'AUTOFLOWER';
  readonly dominance?: Dominance;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly diaries: number;
  readonly reviewCount: number;
  readonly overall?: number;
}

export interface BreederListItem {
  readonly id: string;
  readonly name: string;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly strains: number;
  readonly diaries: number;
  readonly reviewCount: number;
  readonly overall?: number;
}

/** Card de uma strain na página do banco de sementes. */
export interface BreederStrainCard {
  readonly id: string;
  readonly name: string;
  readonly floweringType?: 'PHOTOPERIOD' | 'AUTOFLOWER';
  readonly dominance?: Dominance;
  readonly floweringSpeed?: FloweringSpeed;
  readonly genetics?: string;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly diaries: number;
  readonly reviewCount: number;
  readonly overall?: number;
  readonly avgGramsPerPlant?: number;
  /** Miniatura da foto mais recente de um diário público com a strain. */
  readonly coverThumbnailUrl?: string;
}

export interface ProductListItem {
  readonly id: string;
  readonly name: string;
  readonly brandId: string;
  readonly brand: string;
  readonly category: ProductCategory;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly diaries: number;
  /** Receitas públicas de nutrição que usam o produto. */
  readonly recipes: number;
  /** Notas recebidas nas reviews pós-colheita e a média delas. */
  readonly reviewCount: number;
  readonly rating?: number;
}

/** Nota de um produto numa review pós-colheita. Sem autor; {@code diaryId} só se o diário for público. */
export interface ProductReview {
  readonly id: string;
  readonly strainId: string;
  readonly strain: string;
  readonly breeder?: string;
  readonly rating: number;
  readonly diaryId?: string;
  readonly createdAt: string;
}

/** Andamento do teste da comunidade (cultivadores e avaliações de fora do breeder). */
export interface TestingProgress {
  readonly testers: number;
  readonly testersRequired: number;
  readonly reviews: number;
  readonly reviewsRequired: number;
}

export interface TestingStrainItem {
  readonly id: string;
  readonly name: string;
  readonly breederId: string;
  readonly breeder: string;
  readonly floweringType?: 'PHOTOPERIOD' | 'AUTOFLOWER';
  readonly dominance?: Dominance;
  readonly genetics?: string;
  readonly status: CatalogStatus;
  readonly createdAt: string;
  readonly promotedAt?: string;
  /** Só enquanto está em teste. */
  readonly progress?: TestingProgress;
}

export interface StrainTraits {
  readonly floweringType?: 'PHOTOPERIOD' | 'AUTOFLOWER';
  readonly dominance?: Dominance;
  readonly floweringSpeed?: FloweringSpeed;
  readonly seedTypes?: SeedKind[];
  readonly flavors?: string[];
  readonly effects?: string[];
  readonly genetics?: string;
}

export interface StrainDetail extends StrainTraits {
  readonly id: string;
  readonly name: string;
  readonly breederId?: string;
  readonly breeder?: string;
  readonly status: CatalogStatus;
  readonly source: CatalogSource;
  readonly diaries: number;
  readonly reviewCount: number;
  readonly overall?: number;
  readonly avgGramsPerPlant?: number;
  /** Quando a semente de breeder saiu do teste e entrou no banco oficial. */
  readonly promotedAt?: string;
  /** Andamento do teste (só para strains em teste). */
  readonly testing?: TestingProgress;
}

export interface RankingRow {
  readonly position: number;
  readonly id: string;
  readonly name: string;
  readonly parentName?: string;
  readonly category?: ProductCategory;
  readonly diaries: number;
  readonly growers: number;
  readonly avgGramsPerPlant?: number;
}

/**
 * Catálogo da comunidade: autocomplete, "achar ou cadastrar" sem duplicar, detalhes, curadoria e rankings.
 */
@Injectable({ providedIn: 'root' })
export class CatalogDbApi {
  private readonly http = inject(HttpClient);

  suggest(kind: CatalogKind, q: string, filters: { breederId?: string; brandId?: string; category?: ProductCategory } = {}): Observable<CatalogItem[]> {
    return this.http.get<CatalogItem[]>(`/api/v1/catalog/${kind}/suggest`, { params: compact({ q, ...filters }) });
  }

  resolve(kind: CatalogKind, body: { name: string; confirmNew: boolean; breederId?: string; brandId?: string; category?: ProductCategory }): Observable<Resolution> {
    return this.http.post<Resolution>(`/api/v1/catalog/${kind}/resolve`, compact(body));
  }

  strains(q: string, page = 0): Observable<Page<StrainListItem>> {
    return this.http.get<Page<StrainListItem>>('/api/v1/catalog/strains', { params: compact({ q, page, size: 20 }) });
  }

  breeders(q: string, page = 0): Observable<Page<BreederListItem>> {
    return this.http.get<Page<BreederListItem>>('/api/v1/catalog/breeders', { params: compact({ q, page, size: 20 }) });
  }

  products(q: string, category?: ProductCategory, page = 0): Observable<Page<ProductListItem>> {
    return this.http.get<Page<ProductListItem>>('/api/v1/catalog/products', {
      params: compact({ q, category, page, size: 20 }),
    });
  }

  strain(id: string): Observable<StrainDetail> {
    return this.http.get<StrainDetail>(`/api/v1/catalog/strains/${id}`);
  }

  /** Sementes de breeders em teste pela comunidade (fora da listagem oficial). */
  testingStrains(q: string, breederId?: string, page = 0, size = 20): Observable<Page<TestingStrainItem>> {
    return this.http.get<Page<TestingStrainItem>>('/api/v1/catalog/strains/testing', {
      params: compact({ q, breederId, page, size }),
    });
  }

  product(id: string): Observable<ProductListItem> {
    return this.http.get<ProductListItem>(`/api/v1/catalog/products/${id}`);
  }

  productReviews(id: string, page = 0): Observable<Page<ProductReview>> {
    return this.http.get<Page<ProductReview>>(`/api/v1/catalog/products/${id}/reviews`, { params: { page, size: 20 } });
  }

  breeder(id: string): Observable<BreederListItem> {
    return this.http.get<BreederListItem>(`/api/v1/catalog/breeders/${id}`);
  }

  breederStrains(id: string, page = 0, size = 24): Observable<Page<BreederStrainCard>> {
    return this.http.get<Page<BreederStrainCard>>(`/api/v1/catalog/breeders/${id}/strains`, { params: { page, size } });
  }

  ranking(kind: RankingKind, category?: ProductCategory, page = 0): Observable<Page<RankingRow>> {
    return this.http.get<Page<RankingRow>>(`/api/v1/catalog/rankings/${kind}`, { params: compact({ category, page, size: 20 }) });
  }

  /** Completa informações que faltam (moderadores podem corrigir qualquer campo). */
  contribute(strainId: string, traits: StrainTraits): Observable<{ changed: boolean }> {
    return this.http.patch<{ changed: boolean }>(`/api/v1/catalog/strains/${strainId}`, compact(traits));
  }

  verify(kind: CatalogKind, id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/catalog/${kind}/${id}/verify`, {});
  }

  /** Junta o item duplicado no principal (moderação); o nome do duplicado vira apelido. */
  merge(kind: CatalogKind, duplicateId: string, targetId: string): Observable<CatalogItem> {
    return this.http.post<CatalogItem>(`/api/v1/catalog/${kind}/${duplicateId}/merge`, { targetId });
  }

  /** Recusa um cadastro que ninguém usa (moderação). */
  reject(kind: CatalogKind, id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/catalog/${kind}/${id}`);
  }
}
