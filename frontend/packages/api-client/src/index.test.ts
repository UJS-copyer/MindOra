import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError, createApiClient } from './index';
import type { ApiFetcher } from './index';

describe('createApiClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('prefixes requests with the configured base URL and returns response data', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: { status: 'up' },
        traceId: 'trace-1'
      })
    }));

    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });
    const result = await client.get<{ status: string }>('/api/v1/public/health');

    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/v1/public/health', {
      headers: { Accept: 'application/json' },
      method: 'GET'
    });
    expect(result.data.status).toBe('up');
  });

  it('uses global fetch when no fetcher is provided', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: { status: 'up' },
        traceId: 'trace-2'
      })
    }));
    vi.stubGlobal('fetch', fetcher);

    const client = createApiClient({ baseUrl: 'http://localhost:8080' });
    const result = await client.get<{ status: string }>('/api/v1/public/health');

    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/v1/public/health', {
      headers: { Accept: 'application/json' },
      method: 'GET'
    });
    expect(result.data.status).toBe('up');
  });

  it('throws an ApiClientError for error envelopes', async () => {
    const fetcher = vi.fn(async () => ({
      ok: false,
      status: 409,
      json: async () => ({
        code: 'user_email_exists',
        message: 'Email already exists',
        traceId: 'trace-error'
      })
    }));

    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });

    await expect(client.get('/api/v1/auth/register')).rejects.toMatchObject({
      name: 'ApiClientError',
      code: 'user_email_exists',
      message: 'Email already exists',
      status: 409,
      traceId: 'trace-error'
    } satisfies Partial<ApiClientError>);
  });

  it('sends json mutations with bearer auth', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: { id: 'article-1', status: 'draft' },
        traceId: 'trace-article'
      })
    }));

    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetcher,
      authToken: () => 'Bearer admin-token'
    });
    const result = await client.createArticle({
      title: 'Spring Notes',
      slug: 'spring-notes',
      summary: 'A short summary',
      body: '# Spring',
      visibility: 'public',
      tagIds: []
    });

    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/v1/admin/articles', {
      body: JSON.stringify({
        title: 'Spring Notes',
        slug: 'spring-notes',
        summary: 'A short summary',
        body: '# Spring',
        visibility: 'public',
        tagIds: []
      }),
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer admin-token',
        'Content-Type': 'application/json'
      },
      method: 'POST'
    });
    expect(result.data.status).toBe('draft');
  });

  it('builds public article filter query parameters', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: [],
        traceId: 'trace-list'
      })
    }));

    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });
    await client.listPublicArticles({ categoryId: 'cat-1', tagId: 'tag-1' });

    expect(fetcher).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/public/articles?categoryId=cat-1&tagId=tag-1',
      {
        headers: { Accept: 'application/json' },
        method: 'GET'
      }
    );
  });

  it('uploads a cover asset as multipart form data', async () => {
    const fetcher = vi.fn<ApiFetcher>(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: {
          id: 'asset-1',
          fileName: 'cover.png',
          mimeType: 'image/png',
          size: 4,
          assetType: 'blog_cover',
          publicUrl: '/api/v1/public/assets/asset-1',
          createdAt: '2026-07-14T00:00:00Z'
        },
        traceId: 'trace-asset'
      })
    }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetcher,
      authToken: 'Bearer admin-token'
    });
    const file = new File(['data'], 'cover.png', { type: 'image/png' });

    const result = await client.uploadAsset(file, 'blog_cover');
    const request = fetcher.mock.calls[0]?.[1];

    expect(request?.method).toBe('POST');
    expect(request?.headers).toEqual({
      Accept: 'application/json',
      Authorization: 'Bearer admin-token'
    });
    expect(request?.body).toBeInstanceOf(FormData);
    expect(result.data.assetType).toBe('blog_cover');
  });

  it('supports taxonomy update and delete endpoints', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: { id: 'cat-1', name: '产品工程' },
        traceId: 'trace-taxonomy'
      })
    }));
    const client = createApiClient({
      baseUrl: 'http://localhost:8080',
      fetcher,
      authToken: 'Bearer admin-token'
    });

    await client.updateCategory('cat-1', '产品工程');
    await client.deleteTag('tag-1');

    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/v1/admin/categories/cat-1',
      {
        body: JSON.stringify({ name: '产品工程' }),
        headers: {
          Accept: 'application/json',
          Authorization: 'Bearer admin-token',
          'Content-Type': 'application/json'
        },
        method: 'PUT'
      }
    );
    expect(fetcher).toHaveBeenNthCalledWith(2, 'http://localhost:8080/api/v1/admin/tags/tag-1', {
      headers: {
        Accept: 'application/json',
        Authorization: 'Bearer admin-token'
      },
      method: 'DELETE'
    });
  });

  it('lists admin assets for reuse', async () => {
    const fetcher = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        code: 'success',
        message: 'OK',
        data: [],
        traceId: 'trace-assets'
      })
    }));
    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });

    await client.listAssets();

    expect(fetcher).toHaveBeenCalledWith('http://localhost:8080/api/v1/admin/assets', {
      headers: { Accept: 'application/json' },
      method: 'GET'
    });
  });
});
