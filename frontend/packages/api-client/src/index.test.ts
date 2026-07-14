import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError, createApiClient } from './index';

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
});
