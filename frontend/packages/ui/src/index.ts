import type { WallpaperSettings } from '@mindora/types';

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

export const DEFAULT_WALLPAPER_SETTINGS: WallpaperSettings = {
  mode: 'aurora',
  blur: 24,
  opacity: 0.85,
  customUrl: ''
};

export const PRESET_WALLPAPERS = {
  aurora: {
    name: '极光流光',
    description: '柔和环境三点流体渐变',
    previewGradient: 'linear-gradient(135deg, rgba(16,185,129,0.3) 0%, rgba(59,130,246,0.3) 50%, rgba(168,85,247,0.3) 100%)'
  },
  mountain: {
    name: '自然雪峰',
    description: '冷色调高山雾凇与毛玻璃',
    imageUrl: 'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1920&q=80'
  },
  minimal: {
    name: '素雅微网格',
    description: '极简点阵网格与通透卡片',
    gridSize: 24
  }
} as const;
