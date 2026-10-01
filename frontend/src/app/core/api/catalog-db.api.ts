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
export type CatalogSource = 'DATASET' | 'COMMUNITY';
export type CatalogStatus = 'UNVERIFIED' | 'VERIFIED';
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
