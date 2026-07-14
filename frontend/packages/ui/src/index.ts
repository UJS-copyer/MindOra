export type StatusTone = 'neutral' | 'success' | 'warning' | 'danger';

export interface StatusBadge {
  label: string;
  tone: StatusTone;
}

export function createStatusBadge(isHealthy: boolean): StatusBadge {
  return {
    label: isHealthy ? 'Online' : 'Offline',
    tone: isHealthy ? 'success' : 'danger'
  };
}
