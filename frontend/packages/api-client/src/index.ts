import type {
  ApiEnvelope,
  ApiErrorEnvelope,
  ArticleDraftInput,
  ArticleFilters,
  Asset,
  AuthData,
  BlogArticle,
  BlogCategory,
  BlogTag,
  HealthData
} from '@mindora/types';
import { joinUrl } from '@mindora/utils';

export type ApiFetcher = (
  input: string,
  init: {
    headers: Record<string, string>;
    method: 'GET' | 'POST' | 'PUT';
    body?: string | FormData;
  }
) => Promise<{
  ok: boolean;
  status?: number;
  json: () => Promise<unknown>;
}>;

export interface ApiClientOptions {
  baseUrl: string;
  fetcher?: ApiFetcher;
  authToken?: string | (() => string | undefined);
}

export class ApiClientError extends Error {
  readonly code: string;
  readonly details?: unknown;
  readonly status?: number;
  readonly traceId?: string;

  constructor(envelope: ApiErrorEnvelope, status?: number) {
    super(envelope.message);
    this.name = 'ApiClientError';
    this.code = envelope.code;
    this.details = envelope.details;
    this.status = status;
    this.traceId = envelope.traceId;
  }
}

export interface ApiClient {
  get<T>(path: string): Promise<ApiEnvelope<T>>;
  post<T, B extends object>(path: string, body: B): Promise<ApiEnvelope<T>>;
  put<T, B extends object>(path: string, body: B): Promise<ApiEnvelope<T>>;
  health(): Promise<ApiEnvelope<HealthData>>;
  login(email: string, password: string): Promise<ApiEnvelope<AuthData>>;
  createCategory(name: string): Promise<ApiEnvelope<BlogCategory>>;
  createTag(name: string): Promise<ApiEnvelope<BlogTag>>;
  listAdminArticles(): Promise<ApiEnvelope<BlogArticle[]>>;
  listCategories(): Promise<ApiEnvelope<BlogCategory[]>>;
  listTags(): Promise<ApiEnvelope<BlogTag[]>>;
  listPublicCategories(): Promise<ApiEnvelope<BlogCategory[]>>;
  listPublicTags(): Promise<ApiEnvelope<BlogTag[]>>;
  uploadAsset(file: File, assetType: string): Promise<ApiEnvelope<Asset>>;
  createArticle(input: ArticleDraftInput): Promise<ApiEnvelope<BlogArticle>>;
  updateArticle(id: string, input: ArticleDraftInput): Promise<ApiEnvelope<BlogArticle>>;
  publishArticle(id: string): Promise<ApiEnvelope<BlogArticle>>;
  unpublishArticle(id: string): Promise<ApiEnvelope<BlogArticle>>;
  listPublicArticles(filters?: ArticleFilters): Promise<ApiEnvelope<BlogArticle[]>>;
  getPublicArticle(slug: string): Promise<ApiEnvelope<BlogArticle>>;
}

export function createApiClient(options: ApiClientOptions): ApiClient {
  const fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);

  async function request<T>(
    method: 'GET' | 'POST' | 'PUT',
    path: string,
    body?: object | FormData
  ): Promise<ApiEnvelope<T>> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const token = typeof options.authToken === 'function' ? options.authToken() : options.authToken;
    if (token) {
      headers.Authorization = token;
    }
    if (body && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
    }
    const response = await fetcher(joinUrl(options.baseUrl, path), {
      ...(body ? { body: body instanceof FormData ? body : JSON.stringify(body) } : {}),
      headers,
      method
    });
    const envelope = (await response.json()) as ApiEnvelope<T> | ApiErrorEnvelope;

    if (!response.ok || envelope.code !== 'success') {
      throw new ApiClientError(envelope as ApiErrorEnvelope, response.status);
    }

    return envelope as ApiEnvelope<T>;
  }

  return {
    get<T>(path: string): Promise<ApiEnvelope<T>> {
      return request<T>('GET', path);
    },
    post<T, B extends object>(path: string, body: B): Promise<ApiEnvelope<T>> {
      return request<T>('POST', path, body);
    },
    put<T, B extends object>(path: string, body: B): Promise<ApiEnvelope<T>> {
      return request<T>('PUT', path, body);
    },
    health() {
      return this.get<HealthData>('/api/v1/public/health');
    },
    login(email: string, password: string) {
      return this.post<AuthData, { email: string; password: string }>('/api/v1/auth/login', {
        email,
        password
      });
    },
    createCategory(name: string) {
      return this.post<BlogCategory, { name: string }>('/api/v1/admin/categories', { name });
    },
    createTag(name: string) {
      return this.post<BlogTag, { name: string }>('/api/v1/admin/tags', { name });
    },
    listAdminArticles() {
      return this.get<BlogArticle[]>('/api/v1/admin/articles');
    },
    listCategories() {
      return this.get<BlogCategory[]>('/api/v1/admin/categories');
    },
    listTags() {
      return this.get<BlogTag[]>('/api/v1/admin/tags');
    },
    listPublicCategories() {
      return this.get<BlogCategory[]>('/api/v1/public/categories');
    },
    listPublicTags() {
      return this.get<BlogTag[]>('/api/v1/public/tags');
    },
    uploadAsset(file: File, assetType: string) {
      const form = new FormData();
      form.set('file', file);
      form.set('assetType', assetType);
      return request<Asset>('POST', '/api/v1/admin/assets', form);
    },
    createArticle(input: ArticleDraftInput) {
      return this.post<BlogArticle, ArticleDraftInput>('/api/v1/admin/articles', input);
    },
    updateArticle(id: string, input: ArticleDraftInput) {
      return this.put<BlogArticle, ArticleDraftInput>(`/api/v1/admin/articles/${id}`, input);
    },
    publishArticle(id: string) {
      return this.post<BlogArticle, Record<string, never>>(
        `/api/v1/admin/articles/${id}/publish`,
        {}
      );
    },
    unpublishArticle(id: string) {
      return this.post<BlogArticle, Record<string, never>>(
        `/api/v1/admin/articles/${id}/unpublish`,
        {}
      );
    },
    listPublicArticles(filters: ArticleFilters = {}) {
      return this.get<BlogArticle[]>(withQuery('/api/v1/public/articles', { ...filters }));
    },
    getPublicArticle(slug: string) {
      return this.get<BlogArticle>(`/api/v1/public/articles/${encodeURIComponent(slug)}`);
    }
  };
}

function withQuery(path: string, params: Record<string, string | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) {
      query.set(key, value);
    }
  });
  const value = query.toString();
  return value ? `${path}?${value}` : path;
}
