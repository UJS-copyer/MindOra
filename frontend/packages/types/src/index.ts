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
