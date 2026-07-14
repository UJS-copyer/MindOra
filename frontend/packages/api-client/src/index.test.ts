import { describe, expect, it, vi } from 'vitest';
import { createApiClient } from './index';

describe('createApiClient', () => {
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
});
