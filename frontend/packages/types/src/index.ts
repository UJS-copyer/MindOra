export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  traceId: string;
}

export interface ApiErrorEnvelope {
  code: string;
  message: string;
  details?: unknown;
  traceId?: string;
}

export interface HealthData {
  status: 'up' | 'down' | string;
}

export interface AuthData {
  userId: string;
  email: string;
  accessToken: string;
}

export interface BlogArticle {
  id: string;
  title: string;
  slug: string;
  summary?: string;
  body: string;
  coverAssetId?: string;
  categoryId?: string;
  tagIds: string[];
  status: 'draft' | 'published' | 'unpublished' | string;
  visibility: 'public' | 'private' | string;
  readCount: number;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface BlogCategory {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface BlogTag {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface Asset {
  id: string;
  fileName: string;
  mimeType: string;
  size: number;
  assetType: string;
  publicUrl: string;
  createdAt: string;
}

export interface ArticleDraftInput {
  title: string;
  slug: string;
  summary?: string;
  body: string;
  coverAssetId?: string;
  categoryId?: string;
  tagIds: string[];
  visibility: 'public' | 'private' | string;
}

export interface ArticleFilters {
  categoryId?: string;
  tagId?: string;
}

export type * from './generated/openapi';
