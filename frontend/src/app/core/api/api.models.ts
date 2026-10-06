/**
 * Tipos das respostas da API (espelham os records do backend). Campos nulos são omitidos pelo backend,
 * por isso aparecem como opcionais.
 */

export interface Page<T> {
  readonly items: T[];
  readonly page: number;
  readonly size: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

export type GrowthStage = 'GERMINATION' | 'VEGETATIVE' | 'FLOWERING' | 'DRYING_CURING';
export type GrowMedium = 'LIVING_SOIL' | 'MINERAL' | 'INERT';
export type FloweringType = 'PHOTOPERIOD' | 'AUTOFLOWER';
export type SeedSex = 'REGULAR' | 'FEMINIZED' | 'CLONE';
export type DiaryStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type Visibility = 'PUBLIC' | 'PRIVATE';
export type GrowEnvironment = 'INDOOR' | 'OUTDOOR' | 'GREENHOUSE';
export type ReactionType = 'FIRE' | 'HERB' | 'CLAP';
export type FeedSort = 'RECENTLY_UPDATED' | 'NEWEST' | 'MOST_REACTED';

export interface Harvest {
  readonly dryGrams: number;
  readonly plants: number;
}

export interface SetupSpec {
  readonly tentWidthCm?: number;
  readonly tentDepthCm?: number;
  readonly tentHeightCm?: number;
  readonly lighting?: string;
  readonly lightWattage?: number;
  readonly medium?: GrowMedium;
  readonly nutrients?: string;
  readonly ventilation?: string;
  readonly notes?: string;
}

export interface ProductSnapshot {
  readonly id: string;
  readonly name: string;
  readonly brand: string;
  readonly category: string;
}

/** Sem nome/ambiente/spec quando o cultivo não partiu de um setup e só teve produtos informados depois. */
export interface SetupSnapshot {
  readonly name?: string;
  readonly environment?: GrowEnvironment;
  readonly spec?: SetupSpec;
  /** Produtos do catálogo (fertilizantes, substratos...). */
  readonly products?: ProductSnapshot[];
}

export interface GrowSetup extends SetupSnapshot {
  readonly name: string;
  readonly environment: GrowEnvironment;
  readonly spec: SetupSpec;
  readonly id: string;
}

/** Card do feed e cabeçalho do diário ({@code GET /diaries/{id}}). */
export interface DiaryCard {
  readonly id: string;
  readonly title: string;
  readonly author: string;
  readonly strainName: string;
  readonly breeder?: string;
  /** Strain no catálogo da comunidade. */
  readonly strainId?: string;
  readonly floweringType: FloweringType;
  readonly seedSex: SeedSex;
  readonly medium: GrowMedium;
  readonly status: DiaryStatus;
  readonly visibility: Visibility;
  readonly currentStage: GrowthStage;
  readonly startedOn: string;
  readonly endedOn?: string;
  readonly lastUpdateOn?: string;
  readonly currentDay: number;
  readonly currentWeek: number;
  readonly harvest?: Harvest;
  readonly updatesCount: number;
  readonly commentsCount: number;
  readonly reactionsCount: number;
  readonly coverPhotoId?: string;
  readonly setup?: SetupSnapshot;
  /** Miniatura da capa (WebP ~400px): é o que o feed deve exibir. */
  readonly coverThumbnailUrl?: string;
  /** Retido pela moderação: privado e travado até a decisão (só o dono vê). */
  readonly moderationHold?: boolean;
  /** O cultivo já tem avaliação publicada (cada diário avalia uma vez só). */
  readonly reviewed?: boolean;
}

/** Resposta das operações de escrita em diários. */
export interface GrowDiaryView {
  readonly id: string;
  readonly status: DiaryStatus;
  readonly visibility: Visibility;
  readonly harvest?: Harvest;
}

export interface DiaryUpdate {
  readonly id: string;
  readonly stage: GrowthStage;
  readonly recordedOn: string;
  readonly day: number;
  readonly week: number;
  readonly note?: string;
  readonly temperatureCelsius?: number;
  readonly relativeHumidity?: number;
  readonly wateringPh?: number;
  readonly plantHeightCm?: number;
  readonly photoIds: string[];
  readonly photos: DiaryPhotoRef[];
}

/**
 * Foto de um update. Listas e cards usam só {@code thumbnailUrl}; {@code fullUrl} (1600px) é buscada
 * sob demanda, quando o grower abre a foto para inspecionar.
 */
export interface DiaryPhotoRef {
  readonly id: string;
  readonly thumbnailUrl: string;
  readonly fullUrl: string;
}

export interface Comment {
  readonly id: string;
  readonly author: string;
  readonly authorHasAvatar: boolean;
  readonly byDiaryAuthor: boolean;
  readonly body: string;
  readonly createdAt: string;
  readonly deletable: boolean;
  /** Oculto do público aguardando a moderação (só o autor vê). */
  readonly underReview?: boolean;
  /** Comentário raiz respondido (só nas respostas; um nível só). */
  readonly parentId?: string;
  /** Contagens anônimas e as reações de quem está vendo. */
  readonly reactions?: ReactionSummary;
  /** Total de respostas visíveis (só nos comentários raiz). */
  readonly replyCount?: number;
  /** As primeiras respostas, em ordem cronológica; o resto vem pelo "ver mais". */
  readonly replies?: Comment[];
}

export interface ReactionSummary {
  readonly counts: Record<ReactionType, number>;
  readonly mine: ReactionType[];
}

export interface StrainSummary {
  readonly id: string;
  readonly name: string;
  readonly breeder?: string;
  readonly reviewCount: number;
  readonly overall: number;
  readonly vigor: number;
  readonly pestResistance: number;
  readonly easeOfGrowth: number;
  readonly yield: number;
  readonly terpeneProfile: number;
}

export interface StrainReview {
  readonly id: string;
  readonly author: string;
  readonly diaryId?: string;
  readonly vigor: number;
  readonly pestResistance: number;
  readonly easeOfGrowth: number;
  readonly yield: number;
  readonly terpeneProfile: number;
  readonly report: string;
  readonly createdAt: string;
}

export interface SeedbankSummary {
  readonly id: string;
  readonly name: string;
  readonly reviewCount: number;
  readonly overall: number;
  readonly germination: number;
  readonly stability: number;
  readonly service: number;
}

export interface SeedbankReview {
  readonly id: string;
  readonly author: string;
  readonly diaryId?: string;
  readonly strain: string;
  readonly germination: number;
  readonly stability: number;
  readonly service: number;
  readonly report: string;
  readonly createdAt: string;
}

export interface MyProfile {
  readonly id: string;
  readonly username: string;
  readonly bio?: string;
  readonly hasAvatar: boolean;
  readonly role: string;
  /** Contas antigas ainda sem frase de recuperação geram a frase no próximo login. */
  readonly hasRecoveryPhrase: boolean;
  /** Versão dos Termos de Uso aceita e quando. */
  readonly termsVersion?: string;
  readonly termsAcceptedAt?: string;
  /** Aceitou a versão vigente? Sem isso, a API não aceita publicações. */
  readonly termsAccepted: boolean;
  readonly currentTermsVersion: string;
  /** Bio e foto ocultadas do público pela moderação. */
  readonly profileHidden: boolean;
}

export interface GrowerProfile {
  readonly username: string;
  readonly bio?: string;
  readonly hasAvatar: boolean;
  readonly diaries: number;
  readonly likes: number;
  /** Colheitas registradas em diários públicos. */
  readonly harvests: number;
  readonly gramsPerPlant?: number;
  /** Avaliações de genéticas publicadas. */
  readonly reviews: number;
  readonly followers: number;
  readonly followedByViewer: boolean;
  readonly isViewer: boolean;
}

export interface RankedGrowerDto {
  readonly position: number;
  readonly username: string;
  readonly hasAvatar: boolean;
  readonly diaries: number;
  readonly likes: number;
  readonly gramsPerPlant?: number;
  readonly reviews: number;
}

export interface FollowedGrower {
  readonly username: string;
  readonly hasAvatar: boolean;
}

export interface UploadedImage {
  readonly id: string;
  readonly width: number;
  readonly height: number;
}

/** Corpo de erro RFC 9457 devolvido pelo backend. */
export interface Problem {
  readonly status: number;
  readonly detail?: string;
  readonly violations?: { field: string; message: string }[];
}

/** Setup que o grower já usou em diários públicos (a cópia congelada do cultivo). */
export interface PublicSetup {
  readonly setup: SetupSnapshot;
  readonly diaries: number;
  readonly lastStartedOn: string;
}

/** Review no perfil de quem escreveu. {@code diaryId} só vem se o diário for público. */
export interface AuthoredReview {
  readonly id: string;
  readonly strainId: string;
  readonly strain: string;
  readonly breeder?: string;
  readonly diaryId?: string;
  readonly overall: number;
  readonly report: string;
  readonly createdAt: string;
}
