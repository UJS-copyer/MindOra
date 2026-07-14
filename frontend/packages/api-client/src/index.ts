import type { ApiEnvelope, ApiErrorEnvelope, HealthData } from '@mindora/types';
import { joinUrl } from '@mindora/utils';

export type ApiFetcher = (
  input: string,
  init: {
    headers: Record<string, string>;
    method: 'GET';
  }
) => Promise<{
  ok: boolean;
  status?: number;
  json: () => Promise<unknown>;
}>;

export interface ApiClientOptions {
  baseUrl: string;
  fetcher?: ApiFetcher;
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
  health(): Promise<ApiEnvelope<HealthData>>;
}

export function createApiClient(options: ApiClientOptions): ApiClient {
  const fetcher = options.fetcher ?? window.fetch.bind(window);

  return {
    async get<T>(path: string): Promise<ApiEnvelope<T>> {
      const response = await fetcher(joinUrl(options.baseUrl, path), {
        headers: { Accept: 'application/json' },
        method: 'GET'
      });
      const envelope = (await response.json()) as ApiEnvelope<T> | ApiErrorEnvelope;

      if (!response.ok || envelope.code !== 'success') {
        throw new ApiClientError(envelope as ApiErrorEnvelope, response.status);
      }

      return envelope as ApiEnvelope<T>;
    },
    health() {
      return this.get<HealthData>('/api/v1/public/health');
    }
  };
}
